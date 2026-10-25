#version 150

in vec4 vertexColor;
in vec2 pathCoordinate;

uniform vec4 ColorModulator;
uniform float FlowTime;

out vec4 fragColor;

void main() {
    float side = abs(pathCoordinate.y * 2.0 - 1.0);
    float core = 1.0 - smoothstep(0.26, 0.26 + max(fwidth(side), 0.1), side);
    float halo = exp(-side * side * 5.0);
    float age = fract(FlowTime * 0.18 - pathCoordinate.x / 32.0);
    float trail = pow(1.0 - age, 1.35);
    float alpha = 0.5 * (halo * 0.2 + core * 0.8) * trail * vertexColor.a;
    if (alpha < 0.003) discard;
    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
