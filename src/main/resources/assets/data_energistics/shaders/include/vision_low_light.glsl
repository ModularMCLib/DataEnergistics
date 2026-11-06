uniform float LowLightEnabled;
uniform float Gain;
uniform float AutomaticBrightness;
uniform float Noise;
uniform float InfraredEnabled;
uniform float FrameTime;
uniform mat4 InverseProjection;

float sceneLuminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

vec3 lowLightImage(vec3 scene) {
    float luminance = sceneLuminance(scene);
    // Reject the lightmap's black-level pedestal rather than amplifying it into a bright cave.
    float signal = max(luminance - 0.025, 0.0);
    if (InfraredEnabled > 0.5) {
        float depth = texture(SceneDepthSampler, texCoord).r;
        if (depth < 1.0) {
            vec4 projected = InverseProjection * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec3 viewPosition = projected.xyz / projected.w;
            float distance = length(viewPosition);
            float cone = smoothstep(0.72, 0.94, -viewPosition.z / max(distance, 0.001));
            float attenuation = 1.0 - smoothstep(1.0, 12.0, distance);
            // Depth limits the reflected illuminator to the visible surface, never a wall behind it.
            signal += luminance * 8.0 * cone * attenuation;
        }
    }
    float exposure = 1.0;
    if (AutomaticBrightness > 0.5) {
        float average = 0.0;
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            average += sceneLuminance(texture(SceneSampler, (vec2(x, y) + 0.5) / 4.0).rgb);
        }
        exposure = clamp(0.16 / (average / 16.0 + 0.025), 0.25, 4.0);
    }
    float brightness = 1.0 - exp(-signal * Gain * 2.0 * exposure);
    float halo = 0.0;
    for (int side = 0; side < 4; side++) {
        vec2 offset = side < 2 ? vec2(side == 0 ? -5.0 : 5.0, 0.0) : vec2(0.0, side == 2 ? -5.0 : 5.0);
        halo += smoothstep(0.72, 1.0, sceneLuminance(texture(SceneSampler, texCoord + offset * PixelSize).rgb));
    }
    float grain = fract(sin(dot(gl_FragCoord.xy + floor(FrameTime * 3.0), vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    brightness = clamp(brightness + halo * 0.025 + grain * Noise * 0.12 * (1.0 - brightness * 0.7), 0.0, 1.0);
    float vignette = 1.0 - dot(texCoord - 0.5, texCoord - 0.5) * 0.35;
    return (vec3(0.08, 0.96, 0.18) * brightness + vec3(0.12, 0.04, 0.1) * pow(brightness, 4.0)) * vignette;
}
