# Recipe Creator: GUI Options Screen

## Problem

`/ecocrafting create <workstation>` wizard collects permission, XP, cook time,
villager profession, min level, chance, wandering-trader, and villager-XP via
a strict sequential chat-prompt chain (`promptCookTime` → `promptExperience` →
`promptProfession` → `promptMinLevel` → `promptChance` → `promptWanderingTrader`
→ `promptVillagerXp` → `promptId` → `promptPermission`) in
`RecipeCreatorGUI.kt`. There is no GUI screen for these values at all — a
player expecting to click a button to change them is clicking inert border
filler. Only the ingredient-screen toggles (Shapeless/Symmetry/Crafter
Support, lines 244-274) are real GUI buttons today.

## Goal

Replace the chat-prompt chain with a single GUI screen (`openOptions`)
showing one button per applicable field, values visible and editable in any
order, before the final ID/preview/confirm step (which is unchanged).

## Screen layout

6-row menu, same conventions as existing wizard screens (`fillBorder`,
`addWorkstationIcons` in cols 8-9).

Left column (col 2), one button per row, top to bottom, **only the fields
relevant to the current workstation type**:

| Field | Applies to | Icon |
|---|---|---|
| Permission | all types | `NAME_TAG` |
| Cook Time | furnace, blast_furnace, smoker, campfire | `CLOCK` |
| XP | furnace, blast_furnace, smoker, campfire | `EXPERIENCE_BOTTLE` |
| Profession | villager | `VILLAGER_SPAWN_EGG` |
| Min Level | villager | `BOOK` |
| Chance | villager | `REDSTONE` |
| Wandering Trader | villager | `LEAD` |
| Villager XP | villager | `EXPERIENCE_BOTTLE` |

All other types (crafting_table, crafter, smithing_table, stonecutter,
brewing_stand, grindstone, anvil) show only Permission.

Bottom row (row 6):
- col 3: **Back** (red dye) → returns to the Output screen with current
  values preserved.
- col 5: **Confirm/Next** (lime dye) → proceeds to ID entry (or straight to
  preview if editing an existing recipe).

Each button's lore shows the current value, or `(default)` if it's still at
its initial/unset value.

## Interaction model

**Boolean (Wandering Trader):** left-click toggles the var and redraws the
item in place — identical to the existing Shapeless/Symmetry toggle pattern
(`onLeftClick { event, _ -> ...; event.inventory.setItem(...) }`).

**Enum (Profession):** left-click cycles forward through
`[none] + villagerProfessionKeys`; right-click cycles backward. `none` maps
to `state.profession = ""`. Redraws in place, no chat.

**Numeric/string (Permission, Cook Time, XP, Min Level, Chance, Villager
XP):** no in-GUI keyboard exists, so:
- Left-click: `player.closeInventory()`, then send the existing validated
  chat prompt (reusing `promptCookTime`/`promptExperience`/`promptMinLevel`/
  `promptChance`/`promptVillagerXp`/permission-prompt logic), but the
  continuation now reopens `openOptions(player, state)` instead of chaining
  to the next prompt in sequence.
- Right-click: resets the field straight to its default value (no chat) and
  redraws the button.
- Chat prompts additionally accept `none` / `cancel` / `default`
  (case-insensitive) as reset synonyms, in addition to existing per-field
  validation.

## State changes

`WizardState` gains `var permission: String = editingPermission ?: ""` —
permission moves from a final chat-only parameter into wizard state so it
can be edited via a button like every other field. `toPendingRecipe` drops
its `permission` parameter and reads `this.permission` instead.

`openOutputSetup` and `openIngredientSetup` gain an optional
already-collected-state passthrough so the Back button doesn't discard
previously entered values:
- Options screen **Back** → `openOutputSetup(..., existing WizardState field
  values)` → Output screen **Back** → `openIngredientSetup(..., preserving
  parts/shapeless/symmetry/supportCrafter)`.
- Going forward again (Next/Confirm) reuses the preserved values rather than
  constructing a fresh zero-valued `WizardState`.

## Flow after Options screen

- `openMetadata` is replaced: for every type, it now calls
  `openOptions(player, state)` directly (no more type-based dispatch to
  `promptCookTime`/`promptProfession`).
- Options **Confirm**: if `state.editingId != null`, skip straight to
  `openPreview(player, state.toPendingRecipe(state.editingId))`. Otherwise
  run `promptId` (unchanged chat prompt) then `openPreview`.
- `promptPermission` function is removed (superseded by the Permission
  button).
- Preview / `/ecocrafting confirm` / `/ecocrafting cancel` are unchanged.

## Out of scope

- No changes to the ingredient/output screens' slot layout or toggle
  buttons — those already work correctly.
- No on-screen numeric keyboard/anvil-text-input widget — chat prompt stays
  the input method for free-form values, per Minecraft GUI constraints.
- No changes to recipe YAML schema or `saveRecipeYaml`.
