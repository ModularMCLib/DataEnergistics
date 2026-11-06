uniform vec3 SonarOrigin;
uniform float SonarAge;

float sonarEcho(vec3 relativePosition) {
    float distanceFromOrigin = length(relativePosition - SonarOrigin);
    if (distanceFromOrigin > 48.0) return 0.0;
    float elapsed = SonarAge - distanceFromOrigin / 1.2;
    if (elapsed < 0.0) return 0.0;
    float front = exp(-elapsed * 0.75);
    float afterglow = 0.65;
    return max(front, afterglow);
}
