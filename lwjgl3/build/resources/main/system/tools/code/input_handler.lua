-- Translates raw key/mouse events into editor commands.
local InputHandler = {}
InputHandler.__index = InputHandler

function InputHandler.new(editor)
  local self = setmetatable({}, InputHandler)
  self.editor = editor
  return self
end

function InputHandler:handle(event)
  if event.type == 'key' then
    if event.key == 's' and event.ctrl then
      return self.editor:save()
    elseif event.key == 'left' then
      self.editor.cursor:move_left()
      self.editor.cursor:update_selection_to_current()
      return true
    elseif event.key == 'right' then
      self.editor.cursor:move_right()
      self.editor.cursor:update_selection_to_current()
      return true
    end
  end
  return false
end

return InputHandler
