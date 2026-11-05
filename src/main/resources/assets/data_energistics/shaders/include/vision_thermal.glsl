uniform float AmbientTemperature;

vec3 falseColor(float temperature) {
    vec3 cold = vec3(0.01, 0.025, 0.12);
    vec3 mild = vec3(0.18, 0.08, 0.42);
    vec3 warm = vec3(0.95, 0.15, 0.025);
    vec3 hot = vec3(1.0, 0.95, 0.55);
    if (temperature < 0.4) return mix(cold, mild, temperature / 0.4);
    if (temperature < 0.75) return mix(mild, warm, (temperature - 0.4) / 0.35);
    return mix(warm, hot, (temperature - 0.75) / 0.25);
}

vec3 thermalImage(vec3 scene) {
    vec4 heat = texture(HeatSampler, texCoord);
    float depth = texture(SceneDepthSampler, texCoord).r;
    float temperature = mix(depth < 1.0 ? AmbientTemperature : 0.025, heat.r, heat.a);
    // Recover surface shape from depth, so thermal contrast remains present in complete darkness.
    // Visible texture color and the world's lightmap never determine the ambient temperature.
    vec4 projected = InverseProjection * vec4(texCoord * 2.0 - 1.0, min(depth, 0.999999) * 2.0 - 1.0, 1.0);
    vec3 position = projected.xyz / max(abs(projected.w), 0.000001);
    vec3 normal = cross(dFdx(position), dFdy(position));
    normal *= inversesqrt(max(dot(normal, normal), 0.00000001));
    float shape = depth < 1.0 ? dot(normal, normalize(vec3(0.35, 0.65, 0.7))) * 0.035 : 0.0;
    float response = clamp(temperature + shape, 0.0, 1.0);
    float strength = heat.a * smoothstep(0.55, 0.94, temperature);
    vec3 highlight = Palette < 0.5 ? vec3(1.0) : Palette < 1.5 ? falseColor(temperature) : vec3(1.0, 0.52, 0.12);
    if (Fusion > 0.5) {
        vec3 fused = mix(scene, highlight, strength * 0.9);
        if (heat.a > 0.5 && temperature < 0.1) fused = mix(fused, vec3(0.06, 0.2, 0.36), 0.18);
        return fused;
    }
    if (Palette < 0.5) return vec3(response);
    if (Palette < 1.5) return falseColor(response);
    return mix(vec3(response * 0.65), highlight, strength);
}
