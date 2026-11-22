from pathlib import Path
import sys

path = Path(sys.argv[1] if len(sys.argv) > 1 else "project/mobile/app/src/main/java/com/tirror/app/MainActivity.kt")
source = path.read_text(encoding="utf-8")
open_marker = """                MobileHeader(page)
                when (page) {"""
open_replacement = """                MobileHeader(page)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                when (page) {"""
if open_marker not in source:
    raise SystemExit("Could not find Android screen switch to add a bounded scroll viewport")
source = source.replace(open_marker, open_replacement, 1)
close_marker = """                }
            }
        }
    }
}

@Composable
private fun MobileHeader(page: String)"""
close_replacement = """                }
                }
            }
        }
    }
}

@Composable
private fun MobileHeader(page: String)"""
if close_marker not in source:
    raise SystemExit("Could not find Android screen switch end")
source = source.replace(close_marker, close_replacement, 1)
old_list = "            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {"
new_list = "            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {"
if old_list not in source:
    raise SystemExit("Could not find history list")
source = source.replace(old_list, new_list, 1)
path.write_text(source, encoding="utf-8")
print(f"Added a bounded touch-scroll viewport and history list sizing: {path}")
