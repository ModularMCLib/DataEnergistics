#version 150

uniform sampler2D SceneSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D HeatSampler;
uniform sampler2D SonarSampler;
uniform vec2 PixelSize;
uniform float ThermalEnabled;
uniform float SonarEnabled;
uniform float Fusion;
uniform float Palette;
in vec2 texCoord;
out vec4 fragColor;

#moj_import <data_energistics:vision_low_light.glsl>
#moj_import <data_energistics:vision_thermal.glsl>

void main() {
    vec3 scene = texture(SceneSampler, texCoord).rgb;
    if (LowLightEnabled > 0.5) scene = lowLightImage(scene);
    vec3 color = scene;
    if (ThermalEnabled > 0.5) color = thermalImage(scene);
    if (SonarEnabled > 0.5) {
        vec4 center = texture(SonarSampler, texCoord);
        float edge = 0.0;
        for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            vec4 neighbor = texture(SonarSampler, texCoord + vec2(x, y) * PixelSize * 1.5);
            edge = max(edge, abs(center.a - neighbor.a) * max(center.r, neighbor.r));
        }
        color = mix(color, vec3(0.2, 0.95, 1.0), edge * 0.95);
    }
    fragColor = vec4(color, 1.0);
}
