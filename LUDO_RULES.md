# Ludo Rules (Classic — product spec)

Canonical Classic rules for offline (`rules.ts`) and online (`GameEngineService`).

## 1. Players

- 2 / 3 / 4 players.
- Each player has **4 pawns**.

## 2. Game start

- All pawns start in **jail**.
- A pawn leaves jail only on a **6** (onto that color’s start cell).
- After a **6 that is used** for a legal move → extra roll same turn.

## 3. First move (on 6)

Player may:

- Bring a **new** pawn out of jail, **or**
- Move a pawn already on the board.

## 4. Dice

- Values **1–6**.
- Online: server RNG only (client cannot supply dice).

## 5. Three sixes

Consecutive **6 → 6 → 6** in one turn:

- Third six is **voided** (no move).
- Turn cancels → next player.

## 6. Movement

- Clockwise on the shared path.
- Each cell = 1 step.

## 7. Exact home

- Must roll the **exact** remaining steps to finish.
- Example: remaining 2, dice 3 → that pawn cannot move.

## 8–9. Kill & safe

- Land on a single opponent on a **non-safe** cell → they return to jail; killer gets **bonus roll**.
- **Safe cells** (stars + starts): no kill.

## 10. Starting cell safe

- Each color’s start box is always safe.

## 11. Home column

- Private path: only that color’s pawns enter.
- No kills on home path / home triangle.

## 12–13. Win & ranking

- All 4 pawns home → that player wins (rank 1, then 2, 3, 4).
- Classic: remaining players continue until ranked.

## 14. Turn timer

- Online: **20 seconds**; expire → auto skip / pass turn.

## 15. Disconnect

- Brief reconnect window (~30s).
- After timeout: seat kept / AFK (full bot takeover & surrender flows are partial).

## 16. Auto skip

- No legal moves after a roll (including a 6) → turn passes (6 does **not** grant free re-roll).

## 17. Multiple choices

- If several pawns can move → player picks.

## 18–19. Bonus dice

- After **kill** → bonus roll.
- After **used 6** → extra roll.
- Home finish alone does **not** grant a bonus.

## 20. Win priority

- One move = one chosen pawn; server applies that move’s outcomes (home and/or kill as applicable).

## 21–22. Block & double

- Two same-color pawns on a non-safe cell = **block**; opponents cannot **land** there (pass-through allowed).
- Own stack max **2** on non-safe cells.

## 23. Home safe

- No kill in home column / home.

## 24. Game end

- Classic: continue for remaining rankings after first winner.
- Quick Mode end-on-first-win: not a separate mode yet.

## 25–27. Multiplayer / anti-cheat

- Server authoritative: dice, validate move, turn, reject illegal / wrong-turn.
- Clients share the same snapshot / event stream.

## 26. Reconnect

- Restore board, dice, turn, timer from live snapshot when possible.

## 28. Rematch

- Not fully productized yet (same-room rematch UI).

## 29. Bots

- Bots fill seats / play; disconnect → limited AFK handling.
- Easy / Medium / Hard difficulties: not fully implemented.

## Source of truth

| Area | Offline | Online |
|------|---------|--------|
| Rules | `frontend/src/components/game/rules.ts` | `GameEngineService.java` |
| Board geometry | `shared/ludo-board-constants.json` | same classpath copy |
