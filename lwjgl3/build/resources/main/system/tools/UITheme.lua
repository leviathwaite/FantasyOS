local UITheme = {}

UITheme.colors = {
    bg = 0,
    text = 7,
    keyword = 12,
    func = 14,
    num = 9,
    str = 11,
    comment = 13,
    cursor = 10,
    gutter_bg = 1,
    gutter_fg = 6,
    help_bg = 1,
    help_title = 10,
    help_text = 7,
    help_example = 11,
    selection = 2,
    current_line = 1,
    bracket = 10,
    error = 8
}

function UITheme.safe_col(c)
    return c or 7
end

return UITheme
