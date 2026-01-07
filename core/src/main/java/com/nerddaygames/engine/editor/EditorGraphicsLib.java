package com.nerddaygames.engine.editor;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.OneArgFunction;

/**
 * EditorGraphicsLib - Extended graphics bindings for text rendering, shapes, and clipping
 * Provides high-performance drawing operations optimized for code editor use cases
 */
public class EditorGraphicsLib {
    private final Globals globals;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final GlyphLayout glyphLayout;
    
    private Color currentColor = new Color(Color.WHITE);
    private Rectangle clipRect = null;
    private boolean batchActive = false;
    private boolean shapesActive = false;
    
    private final int screenWidth;
    private final int screenHeight;
    
    public EditorGraphicsLib(Globals globals, SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, int width, int height) {
        this.globals = globals;
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.glyphLayout = new GlyphLayout();
        this.screenWidth = width;
        this.screenHeight = height;
    }
    
    public void register() {
        // Text rendering
        globals.set("draw_text", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String text = args.checkjstring(1);
                float x = (float) args.checkdouble(2);
                float y = (float) args.checkdouble(3);
                
                if (!batchActive) batch.begin();
                font.setColor(currentColor);
                font.draw(batch, text, x, y);
                if (!batchActive) batch.end();
                
                return LuaValue.NONE;
            }
        });
        
        globals.set("draw_text_colored", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String text = args.checkjstring(1);
                float x = (float) args.checkdouble(2);
                float y = (float) args.checkdouble(3);
                float r = (float) args.checkdouble(4);
                float g = (float) args.checkdouble(5);
                float b = (float) args.checkdouble(6);
                float a = (float) args.optdouble(7, 1.0);
                
                if (!batchActive) batch.begin();
                font.setColor(r, g, b, a);
                font.draw(batch, text, x, y);
                if (!batchActive) batch.end();
                
                return LuaValue.NONE;
            }
        });
        
        globals.set("measure_text", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String text = arg.checkjstring();
                glyphLayout.setText(font, text);
                return LuaValue.valueOf((int) glyphLayout.width);
            }
        });
        
        globals.set("get_font_metrics", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                LuaTable metrics = new LuaTable();
                glyphLayout.setText(font, "M");
                metrics.set("char_width", LuaValue.valueOf((int) glyphLayout.width));
                metrics.set("line_height", LuaValue.valueOf((int) font.getLineHeight()));
                metrics.set("ascent", LuaValue.valueOf((int) font.getAscent()));
                metrics.set("descent", LuaValue.valueOf((int) font.getDescent()));
                return metrics;
            }
        });
        
        // Shape rendering
        globals.set("fill_rect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.checkdouble(1);
                float y = (float) args.checkdouble(2);
                float w = (float) args.checkdouble(3);
                float h = (float) args.checkdouble(4);
                
                if (!shapesActive) {
                    shapes.begin(ShapeRenderer.ShapeType.Filled);
                }
                shapes.setColor(currentColor);
                shapes.rect(x, y, w, h);
                if (!shapesActive) {
                    shapes.end();
                }
                
                return LuaValue.NONE;
            }
        });
        
        globals.set("draw_rect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x = (float) args.checkdouble(1);
                float y = (float) args.checkdouble(2);
                float w = (float) args.checkdouble(3);
                float h = (float) args.checkdouble(4);
                
                if (!shapesActive) {
                    shapes.begin(ShapeRenderer.ShapeType.Line);
                }
                shapes.setColor(currentColor);
                shapes.rect(x, y, w, h);
                if (!shapesActive) {
                    shapes.end();
                }
                
                return LuaValue.NONE;
            }
        });
        
        globals.set("draw_line", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                float x1 = (float) args.checkdouble(1);
                float y1 = (float) args.checkdouble(2);
                float x2 = (float) args.checkdouble(3);
                float y2 = (float) args.checkdouble(4);
                
                if (!shapesActive) {
                    shapes.begin(ShapeRenderer.ShapeType.Line);
                }
                shapes.setColor(currentColor);
                shapes.line(x1, y1, x2, y2);
                if (!shapesActive) {
                    shapes.end();
                }
                
                return LuaValue.NONE;
            }
        });
        
        // Color management
        globals.set("set_color", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.istable(1)) {
                    LuaTable t = args.checktable(1);
                    float r = (float) t.get(1).optdouble(1.0);
                    float g = (float) t.get(2).optdouble(1.0);
                    float b = (float) t.get(3).optdouble(1.0);
                    float a = (float) t.get(4).optdouble(1.0);
                    currentColor.set(r / 255f, g / 255f, b / 255f, a);
                } else {
                    float r = (float) args.checkdouble(1);
                    float g = (float) args.checkdouble(2);
                    float b = (float) args.checkdouble(3);
                    float a = (float) args.optdouble(4, 1.0);
                    currentColor.set(r / 255f, g / 255f, b / 255f, a);
                }
                return LuaValue.NONE;
            }
        });
        
        // Clipping
        globals.set("set_clip", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                int x = args.checkint(1);
                int y = args.checkint(2);
                int w = args.checkint(3);
                int h = args.checkint(4);
                
                clipRect = new Rectangle(x, y, w, h);
                Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                Gdx.gl.glScissor(x, screenHeight - y - h, w, h);
                
                return LuaValue.NONE;
            }
        });
        
        globals.set("clear_clip", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                clipRect = null;
                Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                return LuaValue.NONE;
            }
        });
        
        // Batch control
        globals.set("begin_batch", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (!batchActive) {
                    batch.begin();
                    batchActive = true;
                }
                return LuaValue.NONE;
            }
        });
        
        globals.set("end_batch", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (batchActive) {
                    batch.end();
                    batchActive = false;
                }
                return LuaValue.NONE;
            }
        });
        
        globals.set("begin_shapes", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (!shapesActive) {
                    shapes.begin(ShapeRenderer.ShapeType.Filled);
                    shapesActive = true;
                }
                return LuaValue.NONE;
            }
        });
        
        globals.set("end_shapes", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (shapesActive) {
                    shapes.end();
                    shapesActive = false;
                }
                return LuaValue.NONE;
            }
        });
        
        // Screen dimensions
        globals.set("screen_width", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(screenWidth);
            }
        });
        
        globals.set("screen_height", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(screenHeight);
            }
        });
    }
    
    public void cleanup() {
        if (batchActive) {
            batch.end();
            batchActive = false;
        }
        if (shapesActive) {
            shapes.end();
            shapesActive = false;
        }
        if (clipRect != null) {
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            clipRect = null;
        }
    }
}
