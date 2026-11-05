#version 150

uniform sampler2D SceneSampler;
uniform sampler2D HeatSampler;
uniform sampler2D SonarSampler;
uniform vec2 PixelSize;
uniform float ThermalEnabled;
uniform float SonarEnabled;
uniform float Fusion;
uniform float Palette;
in vec2 texCoord;
out vec4 fragColor;

vec3 falseColor(float temperature) {
    vec3 cold = vec3(0.01, 0.025, 0.12);
    vec3 mild = vec3(0.18, 0.08, 0.42);
    vec3 warm = vec3(0.95, 0.15, 0.025);
    vec3 hot = vec3(1.0, 0.95, 0.55);
    if (temperature < 0.4) return mix(cold, mild, temperature / 0.4);
    if (temperature < 0.75) return mix(mild, warm, (temperature - 0.4) / 0.35);
    return mix(warm, hot, (temperature - 0.75) / 0.25);
}

void main() {
    vec3 scene = texture(SceneSampler, texCoord).rgb;
    vec3 color = scene;
    if (ThermalEnabled > 0.5) {
        vec4 heat = texture(HeatSampler, texCoord);
        float luminance = dot(scene, vec3(0.2126, 0.7152, 0.0722));
        float temperature = mix(0.12 + luminance * 0.2, heat.r, heat.a);
        if (Palette < 0.5) {
            color = vec3(temperature);
        } else if (Palette < 1.5) {
            color = falseColor(temperature);
        }
        if (Fusion > 0.5 || Palette > 1.5) {
            float strength = heat.a * smoothstep(0.42, 0.9, temperature);
            vec3 highlight = Palette < 0.5 ? vec3(1.0) : Palette < 1.5 ? falseColor(temperature) : vec3(1.0, 0.52, 0.12);
            color = mix(scene, highlight, strength * 0.9);
            if (heat.a > 0.5 && temperature < 0.1) color = mix(color, vec3(0.06, 0.2, 0.36), 0.18);
        }
    }
    if (SonarEnabled > 0.5) {
        float center = texture(SonarSampler, texCoord).a;
        float edge = 0.0;
        for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            float neighbor = texture(SonarSampler, texCoord + vec2(x, y) * PixelSize * 1.5).a;
            edge = max(edge, abs(center - neighbor));
        }
        color = mix(color, vec3(0.2, 0.95, 1.0), edge * 0.95);
    }
    fragColor = vec4(color, 1.0);
}
