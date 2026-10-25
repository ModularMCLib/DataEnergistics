#version 150

in vec4 vertexColor;
in vec2 pathCoordinate;
in vec2 boundaryPosition;
in float wallFraction;

uniform vec4 ColorModulator;
uniform float FlowTime;
uniform float DomainWidth;

out vec4 fragColor;

void main() {
    if (any(lessThan(boundaryPosition, vec2(-0.05))) || any(greaterThan(boundaryPosition, vec2(DomainWidth + 0.05))) || wallFraction < 0.0 || wallFraction > 1.0) discard;
    float side = abs(pathCoordinate.y * 2.0 - 1.0);
    float core = 1.0 - smoothstep(0.12, 0.12 + max(fwidth(side), 0.1), side);
    float halo = exp(-side * side * 5.0);
    float age = fract(FlowTime * 0.18 - pathCoordinate.x / 32.0);
    float trail = pow(1.0 - age, 2.4);
    float head = 1.0 - smoothstep(0.0, 0.08, age);
    vec3 color = mix(vec3(0.95, 0.59, 0.025), vec3(1.0, 0.85, 0.12), trail);
    float alpha = (halo * 0.18 + core * 0.72) * (trail * 0.7 + head * 0.25) * vertexColor.a;
    if (alpha < 0.003) discard;
    fragColor = vec4(color, alpha) * ColorModulator;
}
