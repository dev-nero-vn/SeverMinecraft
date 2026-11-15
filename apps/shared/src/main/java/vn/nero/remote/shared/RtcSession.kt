package vn.nero.remote.shared
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class RtcSession(
    context: Context,
    private val signal: RealtimeSignalClient,
    private val isHost: Boolean,
    private val onRemoteVideo: (VideoTrack) -> Unit = {},
    private val onControl: (JSONObject) -> Unit = {}
) : PeerConnection.Observer {
    private val appContext = context.applicationContext
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var screenTrack: VideoTrack? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    @Volatile private var remoteDescriptionSet = false
    @Volatile private var controllerOfferStarted = false
    private val disposed = AtomicBoolean(false)
    val eglContext get() = egl.eglBaseContext

    init {
        if (initialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
        }
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun startHostCapture(permissionData: Intent) {
        check(isHost) { "Chỉ Host mới được tạo nguồn màn hình" }
        if (screenCapturer != null) return
        val source = factory.createVideoSource(false)
        val helper = SurfaceTextureHelper.create("NeroScreenCapture", egl.eglBaseContext)
        val capturer = ScreenCapturerAndroid(permissionData, object : MediaProjection.Callback() {
            override fun onStop() { screenTrack?.setEnabled(false) }
        })
        capturer.initialize(helper, appContext, source.capturerObserver)
        capturer.startCapture(720, 1280, 15)
        videoSource = source
        textureHelper = helper
        screenCapturer = capturer
        screenTrack = factory.createVideoTrack("nero-screen", source).apply { setEnabled(true) }
    }

    fun startAsController() {
        check(!isHost)
        if (controllerOfferStarted) return
        controllerOfferStarted = true
        ensurePeer()
        peer?.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
        )
        dataChannel = peer?.createDataChannel("nero-control", DataChannel.Init().apply { ordered = true })
        observeDataChannel(dataChannel)
        peer?.createOffer(sdpObserver(onCreated = { offer ->
            peer?.setLocalDescription(sdpObserver(onSet = {
                val local = peer?.localDescription ?: offer
                signal.send("offer", JSONObject().put("type", local.type.canonicalForm()).put("sdp", local.description))
            }), offer)
        }), MediaConstraints())
    }

    fun handleSignal(event: String, payload: JSONObject) {
        when (event) {
            "offer" -> {
                if (!isHost) return
                ensurePeer()
                screenTrack?.let { peer?.addTrack(it, listOf("nero-stream")) }
                val desc = SessionDescription(SessionDescription.Type.OFFER, payload.optString("sdp"))
                peer?.setRemoteDescription(sdpObserver(onSet = {
                    remoteDescriptionSet = true
                    applyPendingIce()
                    peer?.createAnswer(sdpObserver(onCreated = { answer ->
                        peer?.setLocalDescription(sdpObserver(onSet = {
                            val local = peer?.localDescription ?: answer
                            signal.send("answer", JSONObject().put("type", local.type.canonicalForm()).put("sdp", local.description))
                        }), answer)
                    }), MediaConstraints())
                }), desc)
            }
            "answer" -> {
                if (isHost) return
                val desc = SessionDescription(SessionDescription.Type.ANSWER, payload.optString("sdp"))
                peer?.setRemoteDescription(sdpObserver(onSet = {
                    remoteDescriptionSet = true
                    applyPendingIce()
                }), desc)
            }
            "ice" -> {
                val candidate = IceCandidate(payload.optString("sdpMid"), payload.optInt("sdpMLineIndex"), payload.optString("candidate"))
                if (remoteDescriptionSet) peer?.addIceCandidate(candidate)
                else synchronized(pendingIce) { pendingIce.add(candidate) }
            }
            "resume" -> screenTrack?.setEnabled(true)
        }
    }

    private fun applyPendingIce() {
        val queued = synchronized(pendingIce) {
            pendingIce.toList().also { pendingIce.clear() }
        }
        queued.forEach { peer?.addIceCandidate(it) }
    }

    fun setScreenEnabled(enabled: Boolean) { screenTrack?.setEnabled(enabled) }
    fun sendControl(command: JSONObject): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return channel.send(DataChannel.Buffer(ByteBuffer.wrap(command.toString().toByteArray(Charsets.UTF_8)), false))
    }

    private fun ensurePeer() {
        if (peer != null) return
        val ice = listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        val config = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(config, this)
        check(peer != null) { "Không tạo được WebRTC PeerConnection" }
    }

    private fun sdpObserver(onCreated: (SessionDescription) -> Unit = {}, onSet: () -> Unit = {}) =
        object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) = onCreated(desc)
            override fun onSetSuccess() = onSet()
            override fun onCreateFailure(error: String) {}
            override fun onSetFailure(error: String) {}
        }

    private fun observeDataChannel(channel: DataChannel?) {
        channel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                try { onControl(JSONObject(String(bytes, Charsets.UTF_8))) } catch (_: Exception) {}
            }
        })
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
    override fun onIceCandidate(candidate: IceCandidate) {
        signal.send("ice", JSONObject().put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex).put("candidate", candidate.sdp))
    }
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onAddStream(stream: MediaStream) { stream.videoTracks.firstOrNull()?.let(onRemoteVideo) }
    override fun onRemoveStream(stream: MediaStream) {}
    override fun onDataChannel(channel: DataChannel) { dataChannel = channel; observeDataChannel(channel) }
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) { (receiver.track() as? VideoTrack)?.let(onRemoteVideo) }
    override fun onTrack(transceiver: RtpTransceiver) { (transceiver.receiver.track() as? VideoTrack)?.let(onRemoteVideo) }
    override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        try { screenCapturer?.stopCapture() } catch (_: Exception) {}
        screenCapturer?.dispose()
        textureHelper?.dispose()
        screenTrack?.dispose()
        videoSource?.dispose()
        dataChannel?.close()
        peer?.close()
        peer?.dispose()
        factory.dispose()
        egl.release()
    }
    companion object { private val initialized = AtomicBoolean(false) }
}
