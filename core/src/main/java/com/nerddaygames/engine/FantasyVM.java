package com.nerddaygames.engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.nerddaygames.engine.graphics.Palette;
import org.luaj.vm2.LuaError;
import java.util.ArrayList;
import java.util.List;
import com.badlogic.gdx.files.FileHandle;

public class FantasyVM {

    public final Profile profile;

    // --- DISPLAY SURFACES ---
    private FrameBuffer osBuffer;
    public FrameBuffer gameBuffer; // Made public for ScriptEngine text coordinate fix
    public FrameBuffer currentTarget; // Made public for ScriptEngine text coordinate fix

    private OrthographicCamera osCamera;
    private OrthographicCamera gameCamera;

    // --- FONTS ---
    public BitmapFont osFont;
    public BitmapFont gameFont;

    // --- TOOLS ---
    public SpriteBatch batch;
    public ShapeRenderer shapes;
    public List<TextureRegion[]> spriteSheets;
    public int activeSheetIndex = 0;

    // --- SPRITE EDITING ---
    private Pixmap spriteSheetPixmap;
    private Texture spriteSheetTexture;

    // --- SUBSYSTEMS ---
    public ScriptEngine scriptEngine;
    public Palette palette;
    public InputManager input;
    public FileSystem fs;
    public Ram ram;

    // --- MEMORY MAP ---
    public static final int MEM_MAP_BASE    = 0x1000;
    public static final int MEM_MAP_WIDTH   = 128;
    public static final int MEM_PALETTE_MAP = 0x5F00;
    public static final int MEM_INPUT       = 0x5F40;

    // --- STATE ---
    public boolean hasCrashed = false;
    public String crashMessage = "";
    public String crashLine = "";
    private boolean isBatchDrawing = false;
    private boolean isShapeDrawing = false;
    private boolean enableTimeout = true;

    public FantasyVM(Profile profile) {
        this(profile, 1); // Default: enable timeout
    }

    public FantasyVM(Profile profile, int timeoutFlag) {
        this.profile = profile;
        this.enableTimeout = (timeoutFlag != 0);

        // 1. INIT TOOLS FIRST (Fix for NullPointerException)
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();

        // 2. Init Graphics Hardware (Uses batch/shapes in setTarget)
        initVideo();

        // 3. Init Subsystems
        this.palette = new Palette();
        this.input = new InputManager();
        this.fs = new FileSystem();
        this.ram = new Ram(); // Ram uses fixed 64KB size

        // Default Palette Mapping
        for(int i=0; i<32; i++) ram.poke(MEM_PALETTE_MAP + i, i);

        // 4. Load Assets
        loadFonts();
        loadSprites();

        // 5. Boot Lua
        this.scriptEngine = new ScriptEngine(this, enableTimeout);
    }

    public void resize(int width, int height) {
        this.profile.width = width;
        this.profile.height = height;
        osCamera.setToOrtho(false, width, height);
        osCamera.update();
        // Update projection matrices after camera resize
        if (currentTarget == osBuffer) {
            batch.setProjectionMatrix(osCamera.combined);
            shapes.setProjectionMatrix(osCamera.combined);
        }
    }

    public com.badlogic.gdx.graphics.Texture getScreenTexture() {
        return osBuffer.getColorBufferTexture();
    }

    private void initVideo() {
        // OS SCREEN (1920x1080)
        osBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, profile.width, profile.height, false);
        osCamera = new OrthographicCamera();
        osCamera.setToOrtho(false, profile.width, profile.height);

        // GAME SCREEN (Retro Resolution)
        gameBuffer = new FrameBuffer(Pixmap.Format.RGB888, profile.gameWidth, profile.gameHeight, false);
        gameBuffer.getColorBufferTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

        gameCamera = new OrthographicCamera();
        gameCamera.setToOrtho(false, profile.gameWidth, profile.gameHeight);

        // Start on OS - set initial target and projection
        setTarget("os");
    }

    private void loadFonts() {
        // OS FONT (JetBrains Mono)
        try {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(fs.resolve("system/JetBrainsMono-Regular.ttf"));
            FreeTypeFontParameter p = new FreeTypeFontParameter();
            p.size = 20;
            p.color = Color.WHITE;
            p.minFilter = TextureFilter.Linear;
            p.magFilter = TextureFilter.Linear;
            p.mono = true; // Force monospace rendering
            osFont = gen.generateFont(p);
            osFont.setUseIntegerPositions(true); // Use integer positions for crisp rendering
            osFont.getData().setScale(1.0f);
            gen.dispose();
        } catch (Exception e) {
            // Fallback
            osFont = new BitmapFont();
        }

        // GAME FONT (PressStart2P)
        try {
            FreeTypeFontGenerator gen = new FreeTypeFontGenerator(fs.resolve("system/PressStart2P.ttf"));
            FreeTypeFontParameter p = new FreeTypeFontParameter();
            p.size = 8;
            p.color = Color.WHITE;
            p.minFilter = TextureFilter.Nearest;
            p.magFilter = TextureFilter.Nearest;
            gameFont = gen.generateFont(p);
            gameFont.setUseIntegerPositions(true);
            gen.dispose();
        } catch (Exception e) { gameFont = new BitmapFont(); }
    }

    // TARGET SWITCHING
    public void setTarget(String target) {
        endDrawing(); // Flush current batch

        // Unbind previous
        if (currentTarget != null) currentTarget.end();

        if ("game".equals(target)) {
            currentTarget = gameBuffer;
            currentTarget.begin();
            batch.setProjectionMatrix(gameCamera.combined);
            shapes.setProjectionMatrix(gameCamera.combined);
        } else {
            currentTarget = osBuffer;
            currentTarget.begin();
            batch.setProjectionMatrix(osCamera.combined);
            shapes.setProjectionMatrix(osCamera.combined);
        }
    }

    public BitmapFont getCurrentFont() {
        return (currentTarget == gameBuffer) ? gameFont : osFont;
    }

    public void render() {
        // Start frame on OS Buffer
        setTarget("os");

        // Clear OS Background (Deep Grey)
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (hasCrashed) {
            drawBSOD();
        } else {
            try {
                scriptEngine.executeFunction("_draw");
            } catch (LuaError e) {
                triggerCrash(e);
            }
        }

        endDrawing();
        if (currentTarget != null) currentTarget.end();
    }

    // --- GRAPHICS API HELPERS ---

    public void beginBatch() {
        if (isShapeDrawing) { shapes.end(); isShapeDrawing = false; }
        if (!isBatchDrawing) { batch.begin(); isBatchDrawing = true; }
    }

    public void beginShapes(ShapeRenderer.ShapeType type) {
        if (isBatchDrawing) { batch.end(); isBatchDrawing = false; }
        if (isShapeDrawing && shapes.getCurrentType() != type) {
            shapes.end();
            isShapeDrawing = false;
        }
        if (!isShapeDrawing) { shapes.begin(type); isShapeDrawing = true; }
    }

    private void endDrawing() {
        if (isBatchDrawing) { batch.end(); isBatchDrawing = false; }
        if (isShapeDrawing) { shapes.end(); isShapeDrawing = false; }
    }

    public void cls(int colorIdx) {
        endDrawing();
        int realColor = ram.peek(MEM_PALETTE_MAP + (colorIdx % 32));
        Color c = palette.get(realColor);
        Gdx.gl.glClearColor(c.r, c.g, c.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    public void rect(int x, int y, int w, int h, int col) {
        beginShapes(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(palette.get(ram.peek(MEM_PALETTE_MAP + (col % 32))));
        shapes.rect(x, y, w, h);
    }

    public int mget(int tx, int ty) {
        if (tx < 0 || tx >= MEM_MAP_WIDTH) return 0;
        int addr = MEM_MAP_BASE + (ty * MEM_MAP_WIDTH) + tx;
        return ram.peek(addr);
    }

    public void mset(int tx, int ty, int spriteId) {
        if (tx < 0 || tx >= MEM_MAP_WIDTH) return;
        int addr = MEM_MAP_BASE + (ty * MEM_MAP_WIDTH) + tx;
        ram.poke(addr, spriteId);
    }

    public void map(int celX, int celY, int sx, int sy, int celW, int celH) {
        TextureRegion[] currentSheet = getActiveSprites();
        if (currentSheet == null) return;
        beginBatch();
        batch.setColor(1, 1, 1, 1);
        for (int y = 0; y < celH; y++) {
            for (int x = 0; x < celW; x++) {
                int spriteId = mget(celX + x, celY + y);
                if (spriteId == 0 || spriteId >= currentSheet.length) continue;
                batch.draw(currentSheet[spriteId], sx + (x * 8), sy + (y * 8));
            }
        }
    }

    public Texture getOsTexture() { return osBuffer.getColorBufferTexture(); }
    public Texture getGameTexture() { return gameBuffer.getColorBufferTexture(); }

    public void update(float delta) {
        if (hasCrashed) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) reboot();
            return;
        }
        input.update();
        for(int i=0; i<8; i++) ram.poke(MEM_INPUT + i, input.btn(i) ? 1 : 0);
        ram.poke2(MEM_INPUT + 16, input.mouseX);
        ram.poke2(MEM_INPUT + 18, input.mouseY);
        ram.poke(MEM_INPUT + 20, input.mouseDownLeft ? 1 : 0);

        try { scriptEngine.executeFunction("_update"); }
        catch (LuaError e) { triggerCrash(e); }
    }

    private void drawBSOD() {
        setTarget("os"); // BSOD always on OS
        Gdx.gl.glClearColor(0.2f, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(osCamera.combined);
        batch.begin();
        osFont.setColor(Color.YELLOW);
        osFont.draw(batch, "** GURU MEDITATION **", 0, profile.height - 100, profile.width, 1, false);
        osFont.setColor(Color.WHITE);
        osFont.draw(batch, crashMessage + "\n" + crashLine, 100, profile.height - 200);
        batch.end();
    }

    private void triggerCrash(LuaError e) {
        hasCrashed = true;
        crashMessage = e.getMessage();
        System.err.println("=== CRASH DETECTED ===");
        System.err.println(crashMessage);
        e.printStackTrace();
    }

    public void reboot() {
        hasCrashed = false;
        try {
            String bootScript = fs.read("system/desktop.lua");
            if (bootScript == null) throw new Exception("Could not read system/desktop.lua");
            scriptEngine.runScript(bootScript, "system/desktop.lua");
            scriptEngine.executeFunction("_init");
        } catch (Exception e) { triggerCrash(new LuaError("Reboot failed: " + e.getMessage())); }
    }

    public void setViewport(Viewport v) { input.setViewport(v); }

    private void loadSprites() {
        spriteSheets = new ArrayList<>();
        loadSheet("sprites.png");
        if (spriteSheets.isEmpty()) spriteSheets.add(new TextureRegion[0]);
    }

    private void loadSheet(String f) {
        try {
            // Load the pixmap for editing (Y=0 at top, PNG format)
            spriteSheetPixmap = new Pixmap(fs.resolve(f));

            // Create texture from pixmap
            spriteSheetTexture = new Texture(spriteSheetPixmap);
            spriteSheetTexture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

            // Split into 8x8 sprites
            TextureRegion[][] tmp = TextureRegion.split(spriteSheetTexture, 8, 8);

            // Flip all regions vertically to fix upside-down rendering
            // PNG has Y=0 at top, OpenGL has Y=0 at bottom
            for(TextureRegion[] row : tmp) {
                for(TextureRegion region : row) {
                    region.flip(false, true);
                }
            }

            // Flatten to 1D array
            TextureRegion[] s = new TextureRegion[tmp.length * tmp[0].length];
            int i = 0;
            for(TextureRegion[] r : tmp) {
                for(TextureRegion c : r) {
                    s[i++] = c;
                }
            }
            spriteSheets.add(s);
        } catch(Exception e) {
            System.err.println("Failed to load sprite sheet: " + e.getMessage());
        }
    }
    public TextureRegion[] getActiveSprites() {
        if (activeSheetIndex >= spriteSheets.size()) return null;
        return spriteSheets.get(activeSheetIndex);
    }

    // === SPRITE PIXEL EDITING ===

    public int sget(int x, int y) {
        if (spriteSheetPixmap == null) return 0;
        // Bounds check: sprite sheet is 128x128
        if (x < 0 || x >= 128 || y < 0 || y >= 128) return 0;

        // Get RGBA from pixmap (Y=0 at top in PNG)
        int rgba = spriteSheetPixmap.getPixel(x, y);

        // Convert RGBA to palette index
        return palette.rgbaToIndex(rgba);
    }

    public void sset(int x, int y, int colorIndex) {
        if (spriteSheetPixmap == null) return;
        // Bounds check: sprite sheet is 128x128
        if (x < 0 || x >= 128 || y < 0 || y >= 128) {
            System.err.println("sset out of bounds: (" + x + "," + y + ")");
            return;
        }
        if (colorIndex < 0 || colorIndex >= 32) return;

        // Get color from palette
        Color c = palette.get(colorIndex);

        // Set pixel in pixmap (Y=0 at top)
        spriteSheetPixmap.setColor(c);
        spriteSheetPixmap.drawPixel(x, y);
    }

    public void refreshSpriteTexture() {
        if (spriteSheetTexture == null || spriteSheetPixmap == null) return;

        // Update texture from pixmap
        spriteSheetTexture.draw(spriteSheetPixmap, 0, 0);
    }

    public boolean saveSpriteSheet(String path) {
        if (spriteSheetPixmap == null) return false;
        try {
            // Write pixmap to PNG file
            com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.local("disk/" + path), spriteSheetPixmap);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save sprite sheet: " + e.getMessage());
            return false;
        }
    }

    public boolean isSpriteSheetLoaded() {
        return spriteSheetPixmap != null && spriteSheetTexture != null;
    }

    // Add this method somewhere in the FantasyVM class (e.g., after saveSpriteSheet / before dispose)
    public void setProjectDir(FileHandle projectRoot) {
        try {
            if (projectRoot != null) {
                if (!projectRoot.exists()) projectRoot.mkdirs();
                this.fs = new FileSystem(projectRoot);
                Gdx.app.log("FantasyVM", "setProjectDir: using project root " + projectRoot.path());
            } else {
                // fallback to default disk/ storage
                this.fs = new FileSystem();
                Gdx.app.log("FantasyVM", "setProjectDir: cleared project root, using default storage");
            }
        } catch (Exception e) {
            Gdx.app.error("FantasyVM", "setProjectDir failed: " + e.getMessage(), e);
        }
    }

    public void dispose() {
        if(osBuffer!=null)osBuffer.dispose();
        if(gameBuffer!=null)gameBuffer.dispose();
        if(batch!=null)batch.dispose();
        if(shapes!=null)shapes.dispose();
        if(osFont!=null)osFont.dispose();
        if(gameFont!=null)gameFont.dispose();
        if(palette!=null)palette.dispose();
        if(spriteSheetPixmap!=null)spriteSheetPixmap.dispose();
        if(spriteSheetTexture!=null)spriteSheetTexture.dispose();
    }

    public void circle(int x, int y, int r, int c, boolean f) {
        beginShapes(f?ShapeRenderer.ShapeType.Filled:ShapeRenderer.ShapeType.Line);
        shapes.setColor(palette.get(ram.peek(MEM_PALETTE_MAP+(c%32))));
        shapes.circle(x, y, r);
    }
    public void line(int x1, int y1, int x2, int y2, int c) {
        beginShapes(ShapeRenderer.ShapeType.Line);
        shapes.setColor(palette.get(ram.peek(MEM_PALETTE_MAP+(c%32))));
        shapes.line(x1, y1, x2, y2);
    }
}
