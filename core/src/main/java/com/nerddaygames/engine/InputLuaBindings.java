package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * InputLuaBindings - exposes raw LibGDX input to Lua:
 *  - key(code): boolean (is down)
 *  - keyp(code): boolean (just pressed since last update) -- tracked here
 *  - keyboard_char(): returns string characters from VM.input.keyboardChar() if available
 *  - mouse(): table { x, y, left, right, click, scroll }
 *  - touch(): array of tables { id, x, y, state }
 *  - controller_count(), controller_name(i), controller_button(ci,btn), controller_buttonp(ci,btn)
 *
 * FantasyVM must call update() on this instance once per frame before executing Lua _update.
 */
public class InputLuaBindings {
    private final org.luaj.vm2.Globals globals;
    private final FantasyVM vm;

    // previous key/button states for *p (just-pressed)
    private final Map<Integer, Boolean> prevKeys = new HashMap<>();
    private final Map<String, Boolean> prevMouse = new HashMap<>();
    private final Map<String, Boolean> prevControllerButtons = new HashMap<>();

    public InputLuaBindings(org.luaj.vm2.Globals globals, FantasyVM vm) {
        this.globals = globals;
        this.vm = vm;
    }

    public void register() {
        // key(code)
        globals.set("key", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int code = arg.checkint();
                try {
                    return LuaValue.valueOf(Gdx.input.isKeyPressed(code));
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        });

        // keyp(code) - uses prevKeys map updated in update()
        globals.set("keyp", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int code = arg.checkint();
                boolean curr = Gdx.input.isKeyPressed(code);
                boolean prev = prevKeys.getOrDefault(code, false);
                return LuaValue.valueOf(curr && !prev);
            }
        });

        // keyboard_char() - delegate to vm.input.keyboardChar() if present
        globals.set("keyboard_char", new ZeroArgFunction() {
            @Override public LuaValue call() {
                try {
                    if (vm != null && vm.input != null) {
                        try {
                            java.lang.reflect.Method m = vm.input.getClass().getMethod("keyboardChar");
                            Object o = m.invoke(vm.input);
                            if (o instanceof String) return LuaValue.valueOf((String) o);
                            if (o instanceof Character) return LuaValue.valueOf(o.toString());
                        } catch (NoSuchMethodException ignored) {}
                    }
                } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });

        // mouse() -> { x, y, left, right, click, scroll }
        globals.set("mouse", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable t = new LuaTable();
                int x = Gdx.input.getX();
                int y = Gdx.graphics.getHeight() - Gdx.input.getY();
                boolean leftPressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
                boolean rightPressed = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

                t.set("x", LuaValue.valueOf(x));
                t.set("y", LuaValue.valueOf(y));
                t.set("left", LuaValue.valueOf(leftPressed ? 1 : 0));
                t.set("right", LuaValue.valueOf(rightPressed ? 1 : 0));

                Boolean prevLeft = prevMouse.getOrDefault("left", false);
                boolean click = leftPressed && !prevLeft;
                t.set("click", LuaValue.valueOf(click));

                // scroll requires input processor integration; expose 0 by default
                t.set("scroll", LuaValue.valueOf(0));
                return t;
            }
        });

        // touch() -> array of touch points (id,x,y,state)
        globals.set("touch", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable tbl = new LuaTable();
                int maxPointers = 10;
                int idx = 1;
                for (int p = 0; p < maxPointers; p++) {
                    try {
                        if (Gdx.input.isTouched(p)) {
                            LuaTable entry = new LuaTable();
                            entry.set("id", LuaValue.valueOf(p));
                            entry.set("x", LuaValue.valueOf(Gdx.input.getX(p)));
                            entry.set("y", LuaValue.valueOf(Gdx.graphics.getHeight() - Gdx.input.getY(p)));
                            entry.set("state", LuaValue.valueOf("down"));
                            tbl.set(idx++, entry);
                        }
                    } catch (Exception ignored) { /* some backends throw if pointer index unsupported */ }
                }
                return tbl;
            }
        });

        // controller_count()
        globals.set("controller_count", new ZeroArgFunction() {
            @Override public LuaValue call() {
                Array<Controller> controllers = Controllers.getControllers();
                int count = controllers != null ? controllers.size : 0;
                return LuaValue.valueOf(count);
            }
        });

        // controller_name(i)
        globals.set("controller_name", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                int i = arg.checkint();
                try {
                    Array<Controller> controllers = Controllers.getControllers();
                    if (controllers != null && i >= 1 && i <= controllers.size) {
                        Controller c = controllers.get(i - 1);
                        if (c != null && c.getName() != null) return LuaValue.valueOf(c.getName());
                    }
                } catch (Exception ignored) {}
                return LuaValue.NIL;
            }
        });

        // controller_button(ci, btn) -> boolean
        globals.set("controller_button", new VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                int ci = args.checkint(1);
                int btn = args.checkint(2);
                try {
                    Array<Controller> controllers = Controllers.getControllers();
                    if (controllers != null && ci >= 1 && ci <= controllers.size) {
                        Controller c = controllers.get(ci - 1);
                        if (c != null) {
                            boolean pressed = false;
                            try { pressed = c.getButton(btn); } catch (Exception ignored) {}
                            return LuaValue.valueOf(pressed);
                        }
                    }
                } catch (Exception ignored) {}
                return LuaValue.FALSE;
            }
        });

        // controller_buttonp(ci, btn) -> boolean (uses prevControllerButtons map)
        globals.set("controller_buttonp", new VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                int ci = args.checkint(1);
                int btn = args.checkint(2);
                String key = ci + ":" + btn;
                boolean cur = false;
                try {
                    Array<Controller> controllers = Controllers.getControllers();
                    if (controllers != null && ci >= 1 && ci <= controllers.size) {
                        Controller c = controllers.get(ci - 1);
                        if (c != null) {
                            try { cur = c.getButton(btn); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
                boolean prev = prevControllerButtons.getOrDefault(key, false);
                return LuaValue.valueOf(cur && !prev);
            }
        });
    }

    // Called each frame from FantasyVM.update to refresh previous states
    public void update() {
        // refresh prevKeys for known keys: ensure prevKeys contains standard KEY_* codes
        try {
            for (Field f : Input.Keys.class.getFields()) {
                try {
                    if (f.getType() == int.class) {
                        int code = f.getInt(null);
                        prevKeys.putIfAbsent(code, Gdx.input.isKeyPressed(code));
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // update prevKeys values based on current state
        // Make a copy of keys to avoid ConcurrentModification
        ArrayList<Integer> keys = new ArrayList<>(prevKeys.keySet());
        for (Integer code : keys) {
            prevKeys.put(code, Gdx.input.isKeyPressed(code));
        }

        // mouse previous
        prevMouse.put("left", Gdx.input.isButtonPressed(Input.Buttons.LEFT));
        prevMouse.put("right", Gdx.input.isButtonPressed(Input.Buttons.RIGHT));

        // controllers: snapshot button states for first 32 buttons of each controller
        Array<Controller> controllers = Controllers.getControllers();
        if (controllers != null) {
            for (int i = 0; i < controllers.size; i++) {
                Controller c = controllers.get(i);
                for (int b = 0; b < 32; b++) {
                    String key = (i + 1) + ":" + b;
                    boolean pressed = false;
                    try { pressed = c.getButton(b); } catch (Exception ignored) {}
                    prevControllerButtons.put(key, pressed);
                }
            }
        }
    }
}
