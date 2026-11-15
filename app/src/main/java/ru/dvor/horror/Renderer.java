package ru.dvor.horror;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** OpenGL ES 2.0: небо, двор, персонажи, ключи, свечения. */
public final class Renderer implements GLSurfaceView.Renderer {
    private static final String[] TEX_FILES = {
            "sand.jpg", "grass.jpg", "facade_a.jpg", "facade_b.jpg", "facade_c.jpg", "fence.png", "tree.png",
            "detail.jpg", "puffer.jpg", "hero_face.png", "hero_back.png", "hero_hair.jpg", "enemy_face.png",
            "enemy_back.png", "enemy_hair.jpg", "white.png", "glow.png", "beam.png"
    };
    private static final String[] CLAMPED = {"hero_face", "hero_back", "enemy_face", "enemy_back", "glow", "beam"};

    /** Программа и кэш uniform-ов. */
    private static final class Prog {
        final int id;
        final HashMap<String, Integer> loc = new HashMap<>();

        Prog(String vs, String fs) {
            int v = compile(GLES20.GL_VERTEX_SHADER, vs), f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
            id = GLES20.glCreateProgram();
            GLES20.glAttachShader(id, v);
            GLES20.glAttachShader(id, f);
            GLES20.glBindAttribLocation(id, 0, "aPos");
            GLES20.glBindAttribLocation(id, 1, "aNrm");
            GLES20.glBindAttribLocation(id, 2, "aUv");
            GLES20.glBindAttribLocation(id, 3, "aCol");
            GLES20.glLinkProgram(id);
            int[] ok = new int[1];
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0);
            if (ok[0] == 0) throw new RuntimeException("link: " + GLES20.glGetProgramInfoLog(id));
        }

        int u(String name) {
            Integer l = loc.get(name);
            if (l == null) {
                l = GLES20.glGetUniformLocation(id, name);
                loc.put(name, l);
            }
            return l;
        }

        private static int compile(int type, String src) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, src);
            GLES20.glCompileShader(s);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) throw new RuntimeException("shader: " + GLES20.glGetShaderInfoLog(s));
            return s;
        }
    }

    /** VBO с вершинами формата MeshBuilder. */
    private static final class Vbo {
        int id, count;
    }

    private final Context ctx;
    final Game game;
    private final Models models = new Models(this);

    private Prog world, sky, sprite;
    private final HashMap<String, Integer> tex = new HashMap<>();
    private final Map<String, Vbo> batches = new java.util.LinkedHashMap<>();
    private Vbo cube, sphere, quad;
    private int skyVbo;
    private int boundVbo = -1;

    private final float[] proj = new float[16], view = new float[16], vp = new float[16];
    private final float[] ident = new float[16], m = new float[16];
    private final float[] lpos = new float[24], lcol = new float[24];
    private int vw = 1, vh = 1;
    private long last;
    float time;

    public Renderer(Context ctx, Game game) {
        this.ctx = ctx;
        this.game = game;
        M4.identity(ident);
    }

    // ------------------------------------------------------------------ ресурсы

    private String readText(String path) {
        try (InputStream in = ctx.getAssets().open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(path, e);
        }
    }

    private void loadTextures() {
        tex.clear();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        for (String f : TEX_FILES) {
            String name = f.substring(0, f.lastIndexOf('.'));
            try (InputStream in = ctx.getAssets().open("tex/" + f)) {
                Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
                int[] id = new int[1];
                GLES20.glGenTextures(1, id, 0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0]);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                boolean clamp = false;
                for (String c : CLAMPED) if (c.equals(name)) clamp = true;
                int wrap = clamp ? GLES20.GL_CLAMP_TO_EDGE : GLES20.GL_REPEAT;
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
                bmp.recycle();
                tex.put(name, id[0]);
            } catch (IOException e) {
                throw new RuntimeException(f, e);
            }
        }
    }

    private Vbo upload(float[] data) {
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).position(0);
        int[] id = new int[1];
        GLES20.glGenBuffers(1, id, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
        Vbo v = new Vbo();
        v.id = id[0];
        v.count = data.length / MeshBuilder.STRIDE;
        return v;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        world = new Prog(readText("shaders/world.vert"), readText("shaders/world.frag"));
        sky = new Prog(readText("shaders/sky.vert"), readText("shaders/sky.frag"));
        sprite = new Prog(readText("shaders/sprite.vert"), readText("shaders/sprite.frag"));
        loadTextures();
        batches.clear();
        for (Map.Entry<String, MeshBuilder> e : game.meshes.entrySet()) batches.put(e.getKey(), upload(e.getValue().toArray()));
        MeshBuilder mb = new MeshBuilder();
        mb.box(0, 0, 0, 1, 1, 1, 1, 1, 1, 0);
        cube = upload(mb.toArray());
        mb = new MeshBuilder();
        mb.sphere(0, 0, 0, 1, 1, 1, 18, 12, 1, 1, 1);
        sphere = upload(mb.toArray());
        mb = new MeshBuilder();
        mb.quad(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0, 0, 0, 1, 1, 1, 1, 1);
        quad = upload(mb.toArray());
        FloatBuffer fb = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
        int[] id = new int[1];
        GLES20.glGenBuffers(1, id, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, id[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, fb, GLES20.GL_STATIC_DRAW);
        skyVbo = id[0];
        boundVbo = -1;
        last = 0;
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        vw = Math.max(1, w);
        vh = Math.max(1, h);
        GLES20.glViewport(0, 0, vw, vh);
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        time += dt;
        game.update(dt);
        render();
    }

    private void bindMesh(Vbo v) {
        if (boundVbo == v.id) return;
        boundVbo = v.id;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, v.id);
        int stride = MeshBuilder.STRIDE * 4;
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, stride, 12);
        GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, stride, 24);
        GLES20.glVertexAttribPointer(3, 3, GLES20.GL_FLOAT, false, stride, 32);
    }

    private void bindTex(String name, int unit) {
        Integer id = tex.get(name);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id == null ? 0 : id);
    }

    private void render() {
        Game g = game;
        GLES20.glClearColor(0.02f, 0.03f, 0.06f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        float[] cp = g.camPos, cf = g.camFwd;
        float aspect = (float) vw / vh;
        M4.perspective(proj, g.fov, aspect, 0.25f, 140f);
        M4.lookAt(view, cp[0], cp[1], cp[2], cp[0] + cf[0], cp[1] + cf[1], cp[2] + cf[2], 0, 1, 0);
        M4.mul(vp, proj, view);
        for (int i = 0; i < 4; i++) GLES20.glEnableVertexAttribArray(i);

        drawSky(cp, cf, aspect);
        GLES20.glUseProgram(world.id);
        setupWorld(cp);
        for (Map.Entry<String, Vbo> e : batches.entrySet()) drawBatch(e.getKey(), e.getValue());
        drawKeysAndGate();
        models.drawEnemy(g);
        models.drawHero(g);
        drawSprites(cp, cf);
        for (int i = 1; i < 4; i++) GLES20.glDisableVertexAttribArray(i);
    }

    private void drawSky(float[] cp, float[] cf, float aspect) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glUseProgram(sky.id);
        float rx = -cf[2], rz = cf[0], rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        // u = r x f
        float ux = -rz * cf[1];
        float uy = rz * cf[0] - rx * cf[2];
        float uz = rx * cf[1] - 0 * cf[0];
        GLES20.glUniform3f(sky.u("uCamR"), rx, 0, rz);
        GLES20.glUniform3f(sky.u("uCamU"), ux, uy, uz);
        GLES20.glUniform3f(sky.u("uCamF"), cf[0], cf[1], cf[2]);
        GLES20.glUniform1f(sky.u("uTanHalf"), (float) Math.tan(game.fov * 0.5));
        GLES20.glUniform1f(sky.u("uAspect"), aspect);
        GLES20.glUniform3f(sky.u("uMoonDir"), 0.45f, 0.62f, -0.64f);
        GLES20.glUniform1f(sky.u("uTime"), time);
        GLES20.glUniform1f(sky.u("uFear"), game.fear);
        GLES20.glUniform2f(sky.u("uRes"), vw, vh);
        for (int i = 1; i < 4; i++) GLES20.glDisableVertexAttribArray(i);
        boundVbo = -1;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        for (int i = 1; i < 4; i++) GLES20.glEnableVertexAttribArray(i);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void setupWorld(float[] cp) {
        Game g = game;
        Prog p = world;
        GLES20.glUniformMatrix4fv(p.u("uVP"), 1, false, vp, 0);
        GLES20.glUniform3f(p.u("uEye"), cp[0], cp[1], cp[2]);
        GLES20.glUniform3f(p.u("uFlashPos"), g.flashPos[0], g.flashPos[1], g.flashPos[2]);
        GLES20.glUniform3f(p.u("uFlashDir"), g.flashDir[0], g.flashDir[1], g.flashDir[2]);
        GLES20.glUniform1f(p.u("uFlash"), g.flashOn ? g.flashPower : 0f);
        GLES20.glUniform3f(p.u("uAmb"), 0.10f, 0.13f, 0.23f);
        GLES20.glUniform3f(p.u("uFogCol"), 0.03f, 0.042f, 0.08f);
        GLES20.glUniform1f(p.u("uFogDen"), 0.026f * (1f + 0.35f * g.fear));
        GLES20.glUniform1f(p.u("uFear"), g.fear);
        GLES20.glUniform1f(p.u("uTime"), time);
        GLES20.glUniform2f(p.u("uRes"), vw, vh);
        GLES20.glUniform1i(p.u("uTex"), 0);
        GLES20.glUniform1i(p.u("uDetail"), 1);
        bindTex("detail", 1);

        java.util.Arrays.fill(lcol, 0f);
        for (int i = 0; i < Game.KEYS_TOTAL; i++) {
            lpos[i * 3] = g.keyX[i];
            lpos[i * 3 + 1] = 1.1f;
            lpos[i * 3 + 2] = g.keyZ[i];
            if (!g.keyTaken[i]) {
                float k = 1.3f + 0.35f * (float) Math.sin(time * 3f + i);
                lcol[i * 3] = 1.0f * k;
                lcol[i * 3 + 1] = 0.72f * k;
                lcol[i * 3 + 2] = 0.22f * k;
            }
        }
        for (int i = 0; i < 2; i++) {
            float[] l = Level.LAMPS[i];
            float f = i == 0 ? g.lampFlicker : 0.92f;
            lpos[(5 + i) * 3] = l[0];
            lpos[(5 + i) * 3 + 1] = l[1] - 0.3f;
            lpos[(5 + i) * 3 + 2] = l[2];
            lcol[(5 + i) * 3] = l[3] * f;
            lcol[(5 + i) * 3 + 1] = l[4] * f;
            lcol[(5 + i) * 3 + 2] = l[5] * f;
        }
        lpos[21] = 0f;
        lpos[22] = 2.5f;
        lpos[23] = Level.FACADE_Z + 0.9f;
        if (g.gateOpen) {
            lcol[21] = 0.2f;
            lcol[22] = 1.5f;
            lcol[23] = 0.4f;
        } else {
            lcol[21] = 1.0f;
            lcol[22] = 0.8f;
            lcol[23] = 0.45f;
        }
        GLES20.glUniform3fv(p.u("uLightPos[0]"), 8, lpos, 0);
        GLES20.glUniform3fv(p.u("uLightCol[0]"), 8, lcol, 0);
    }

    private void drawBatch(String name, Vbo v) {
        float detail = 0.5f, emis = 0f, cut = 0.01f;
        String t = name;
        switch (name) {
            case "sand":
            case "grass":
                detail = 0.9f;
                break;
            case "white":
                t = "white";
                break;
            case "emit":
                t = "white";
                emis = 1.3f;
                detail = 0f;
                break;
            case "fence":
                detail = 0f;
                cut = 0.04f;
                break;
            case "tree":
                detail = 0f;
                cut = 0.3f;
                break;
            default:
                break;
        }
        Prog p = world;
        GLES20.glUniformMatrix4fv(p.u("uModel"), 1, false, ident, 0);
        GLES20.glUniform3f(p.u("uTint"), 1f, 1f, 1f);
        GLES20.glUniform1f(p.u("uEmis"), emis);
        GLES20.glUniform1f(p.u("uCut"), cut);
        GLES20.glUniform1f(p.u("uDetailAmt"), detail);
        bindTex(t, 0);
        bindMesh(v);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, v.count);
    }

    // ------------------------------------------------------------------ динамические объекты (для Models)

    private void draw(Vbo mesh, String texName, float[] model, float r, float g, float b, float emis, float cut) {
        Prog p = world;
        GLES20.glUniformMatrix4fv(p.u("uModel"), 1, false, model, 0);
        GLES20.glUniform3f(p.u("uTint"), r, g, b);
        GLES20.glUniform1f(p.u("uEmis"), emis);
        GLES20.glUniform1f(p.u("uCut"), cut);
        GLES20.glUniform1f(p.u("uDetailAmt"), 0f);
        bindTex(texName, 0);
        bindMesh(mesh);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.count);
    }

    void cube(String texName, float[] model, float r, float g, float b, float emis) {
        draw(cube, texName, model, r, g, b, emis, 0.01f);
    }

    void sphere(String texName, float[] model, float r, float g, float b, float emis) {
        draw(sphere, texName, model, r, g, b, emis, 0.01f);
    }

    void plate(String texName, float[] model, float emis) {
        draw(quad, texName, model, 1f, 1f, 1f, emis, 0.04f);
    }

    // ------------------------------------------------------------------ ключи, ворота, свечения

    private void drawKeysAndGate() {
        Game g = game;
        for (int i = 0; i < Game.KEYS_TOTAL; i++) {
            if (g.keyTaken[i]) continue;
            M4.identity(m);
            M4.translate(m, g.keyX[i], 1.0f + 0.12f * (float) Math.sin(time * 2f + i), g.keyZ[i]);
            M4.rotateY(m, time * 1.6f + i);
            M4.rotateZ(m, 0.35f);
            M4.scale(m, 0.17f, 0.05f, 0.17f);
            sphere("white", m, 1f, 0.78f, 0.2f, 0.9f);
        }
        // створки ворот
        float open = g.gateAnim * 1.75f;
        for (int s = -1; s <= 1; s += 2) {
            M4.identity(m);
            M4.translate(m, s * Level.GATE_HW, 0f, -Level.HZ);
            M4.rotateY(m, -s * open);
            M4.translate(m, -s * Level.GATE_HW * 0.5f, 1.2f, 0f);
            M4.scale(m, Level.GATE_HW, 2.4f, 1f);
            plate("fence", m, 0f);
            M4.identity(m);
            M4.translate(m, s * Level.GATE_HW, 0f, -Level.HZ);
            M4.rotateY(m, -s * open);
            M4.translate(m, -s * Level.GATE_HW * 0.5f, 2.4f, 0f);
            M4.scale(m, Level.GATE_HW, 0.08f, 0.08f);
            cube("white", m, 0.12f, 0.24f, 0.15f, 0f);
            M4.identity(m);
            M4.translate(m, s * Level.GATE_HW, 0f, -Level.HZ);
            M4.rotateY(m, -s * open);
            M4.translate(m, -s * Level.GATE_HW * 0.5f, 0.15f, 0f);
            M4.scale(m, Level.GATE_HW, 0.08f, 0.08f);
            cube("white", m, 0.12f, 0.24f, 0.15f, 0f);
        }
        M4.identity(m);
        M4.translate(m, 0f, 1.1f, -Level.HZ);
        M4.scale(m, 0.16f, 0.16f, 0.12f);
        if (g.gateOpen) cube("white", m, 0.1f, 1f, 0.25f, 1f);
        else cube("white", m, 1f, 0.12f, 0.1f, 1f);
    }

    private void glow(String texName, float x, float y, float z, float[] cf, float sx, float sy,
                      float r, float g, float b, float k, boolean vertical) {
        float rx = -cf[2], rz = cf[0], rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) return;
        rx /= rl;
        rz /= rl;
        if (vertical) {
            M4.billboard(m, x, y, z, rx, 0, rz, 0, 1, 0, sx, sy);
        } else {
            float fx = cf[0], fy = cf[1], fz = cf[2];
            float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
            M4.billboard(m, x, y, z, rx, 0, rz, ux / ul, uy / ul, uz / ul, sx, sy);
        }
        GLES20.glUniformMatrix4fv(sprite.u("uModel"), 1, false, m, 0);
        GLES20.glUniform4f(sprite.u("uColor"), r, g, b, k);
        bindTex(texName, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, quad.count);
    }

    private void drawSprites(float[] cp, float[] cf) {
        Game g = game;
        GLES20.glUseProgram(sprite.id);
        GLES20.glUniformMatrix4fv(sprite.u("uVP"), 1, false, vp, 0);
        GLES20.glUniform1i(sprite.u("uTex"), 0);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
        GLES20.glDepthMask(false);
        boundVbo = -1;
        bindMesh(quad);
        for (int i = 0; i < Game.KEYS_TOTAL; i++) {
            if (g.keyTaken[i]) continue;
            float k = 0.8f + 0.25f * (float) Math.sin(time * 3f + i);
            glow("glow", g.keyX[i], 1.05f, g.keyZ[i], cf, 1.9f, 1.9f, 1f, 0.7f, 0.2f, k, false);
            glow("beam", g.keyX[i], 2.5f, g.keyZ[i], cf, 0.9f, 5f, 1f, 0.75f, 0.3f, 0.55f, true);
        }
        for (int i = 0; i < 2; i++) {
            float[] l = Level.LAMPS[i];
            glow("glow", l[0], l[1] + 0.1f, l[2], cf, 3.2f, 3.2f, l[3], l[4], l[5], (i == 0 ? g.lampFlicker : 0.92f) * 0.8f, false);
        }
        if (g.gateOpen) glow("glow", 0f, 2.7f, Level.FACADE_Z + 0.5f, cf, 3.4f, 3.4f, 0.2f, 1f, 0.4f, 0.9f, false);
        else glow("glow", 0f, 2.7f, Level.FACADE_Z + 0.5f, cf, 2.6f, 2.6f, 1f, 0.8f, 0.45f, 0.55f, false);
        if (g.flashOn) {
            glow("glow", g.flashPos[0] + g.flashDir[0] * 0.25f, g.flashPos[1] + g.flashDir[1] * 0.25f,
                    g.flashPos[2] + g.flashDir[2] * 0.25f, cf, 0.5f, 0.5f, 1f, 0.95f, 0.8f, 0.7f * g.flashPower, false);
        }
        GLES20.glDepthMask(true);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(world.id);
    }
}
