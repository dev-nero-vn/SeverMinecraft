from pathlib import Path

target = Path("project/version_info.txt")
target.write_text(
    """VSVersionInfo(
  ffi=FixedFileInfo(
    filevers=(1, 0, 0, 0),
    prodvers=(1, 0, 0, 0),
    mask=0x3f,
    flags=0x0,
    OS=0x40004,
    fileType=0x1,
    subtype=0x0,
    date=(0, 0)
  ),
  kids=[
    StringFileInfo([
      StringTable(
        "040904B0",
        [
          StringStruct("CompanyName", "LEHAITRIEU"),
          StringStruct("FileDescription", "Tirror - Wireless Android Screen Mirroring"),
          StringStruct("FileVersion", "1.0.0.0"),
          StringStruct("InternalName", "Tirror"),
          StringStruct("LegalCopyright", "Copyright (c) LEHAITRIEU"),
          StringStruct("OriginalFilename", "Tirror.exe"),
          StringStruct("ProductName", "Tirror"),
          StringStruct("ProductVersion", "1.0.0.0")
        ]
      )
    ]),
    VarFileInfo([VarStruct("Translation", [1033, 1200])])
  ]
)
""",
    encoding="utf-8",
)
print(f"Wrote Windows version metadata: {target}")
