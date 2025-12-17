# Code Editor Module (refactor)

This folder contains the new single-responsibility modules for the code editor.

Guidelines:
- Move small, testable logic from the old monolithic core into the appropriate module:
  - file I/O -> file_manager.lua
  - cursor math -> cursor.lua
  - undo/redo semantics -> undo_redo.lua
  - tokenizing -> syntax_highlighter.lua
  - search/replace -> search_replace.lua
  - input mapping -> input_handler.lua
  - rendering glue -> renderer.lua (UI should keep actual drawing calls)
- Keep code.lua as a facade for backwards compatibility. Do not change external callers until module behavior is stable.
- Add unit tests for cursor, undo_redo, search_replace and file_manager logic before porting more complex behavior.

Porting checklist:
- [ ] Create the skeleton (done)
- [ ] Port file load/save code into file_manager
- [ ] Port cursor movement + selection code into cursor.lua and add tests
- [ ] Port undo/redo behavior
- [ ] Port fixes from branches: copilot/fix-cursor-overlapping-text, copilot/fix-code-editor-rendering, copilot/update-save-hotkey-feedback, copilot/add-reliable-saving-loading
- [ ] Add integration tests and manual QA

Testing:
- Focus unit tests on pure logic modules. For renderer and input integrate with UI layer and verify visually.
