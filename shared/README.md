# Shared Ludo board constants

**Source of truth:** `ludo-board-constants.json` in this folder.

Copies that **must stay identical** (same numbers, same color offsets):

1. `backend-spring/src/main/resources/ludo-board-constants.json`
2. `frontend/src/config/ludo-board-constants.json`

Java loads the classpath resource; the React app imports the frontend copy. When you change board geometry, update **all three** files (or copy from this folder).

Do not hardcode divergent path lengths (51 vs 52) or safe indices in the engine or UI.
