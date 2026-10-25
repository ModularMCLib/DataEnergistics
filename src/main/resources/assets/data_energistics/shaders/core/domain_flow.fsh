#version 150

in vec4 vertexColor;
in vec2 pathCoordinate;

uniform vec4 ColorModulator;
uniform float FlowTime;

out vec4 fragColor;

void main() {
    float side = abs(pathCoordinate.y * 2.0 - 1.0);
    float core = 1.0 - smoothstep(0.12, 0.12 + max(fwidth(side), 0.1), side);
    float halo = exp(-side * side * 5.0);
    float head = fract(pathCoordinate.x / 26.0 - FlowTime * 0.3);
    float flow = exp(-head * 12.0) + exp(-(1.0 - head) * 55.0);
    float fine = pow(0.5 + 0.5 * sin(pathCoordinate.x * 1.2 - FlowTime * 4.0), 6.0);
    float intensity = 0.4 + flow * 0.6 + fine * 0.08;
    vec3 gold = vec3(1.0, 0.72, 0.2);
    vec3 light = vec3(1.0, 0.97, 0.65);
    vec3 color = mix(gold, light, core * (0.65 + flow * 0.35));
    float alpha = (halo * 0.25 + core * 0.75) * intensity * vertexColor.a;
    if (alpha < 0.01) discard;
    fragColor = vec4(color, alpha) * ColorModulator;
}
