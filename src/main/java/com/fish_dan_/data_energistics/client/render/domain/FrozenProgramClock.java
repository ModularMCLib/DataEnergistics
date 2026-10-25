package com.fish_dan_.data_energistics.client.render.domain;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Captures the standard Iris time uniforms per program without changing the clock for other draw calls. */
final class FrozenProgramClock {

    private final Int2ObjectOpenHashMap<Program> programs = new Int2ObjectOpenHashMap<>();

    void draw(Runnable draw) {
        int id = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (id == 0) {
            draw.run();
            return;
        }
        var program = programs.computeIfAbsent(id, FrozenProgramClock::capture);
        for (int index = 0; index < program.floatLocations.length; index++) {
            int location = program.floatLocations[index];
            program.liveFloats[index] = GL20.glGetUniformf(id, location);
            GL20.glUniform1f(location, program.frozenFloats[index]);
        }
        for (int index = 0; index < program.intLocations.length; index++) {
            int location = program.intLocations[index];
            program.liveInts[index] = GL20.glGetUniformi(id, location);
            GL20.glUniform1i(location, program.frozenInts[index]);
        }
        try {
            draw.run();
        } finally {
            for (int index = 0; index < program.floatLocations.length; index++) GL20.glUniform1f(program.floatLocations[index], program.liveFloats[index]);
            for (int index = 0; index < program.intLocations.length; index++) GL20.glUniform1i(program.intLocations[index], program.liveInts[index]);
        }
    }

    private static Program capture(int program) {
        var floatLocations = new IntArrayList();
        var floatValues = new FloatArrayList();
        for (String name : new String[] { "frameTimeCounter", "frameTime" }) {
            int location = GL20.glGetUniformLocation(program, name);
            if (location < 0) continue;
            floatLocations.add(location);
            floatValues.add(name.equals("frameTime") ? 0 : GL20.glGetUniformf(program, location));
        }
        var intLocations = new IntArrayList();
        var intValues = new IntArrayList();
        for (String name : new String[] { "frameCounter", "worldTime", "worldDay", "moonPhase" }) {
            int location = GL20.glGetUniformLocation(program, name);
            if (location < 0) continue;
            intLocations.add(location);
            intValues.add(GL20.glGetUniformi(program, location));
        }
        return new Program(floatLocations.toIntArray(), floatValues.toFloatArray(), new float[floatLocations.size()],
                intLocations.toIntArray(), intValues.toIntArray(), new int[intLocations.size()]);
    }

    private record Program(int[] floatLocations, float[] frozenFloats, float[] liveFloats, int[] intLocations, int[] frozenInts, int[] liveInts) {}
}
