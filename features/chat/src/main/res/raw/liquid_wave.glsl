uniform float2 resolution;
uniform float time;
uniform float amplitude;
uniform float intensity;
uniform float3 backgroundColor;
uniform float3 primaryColor;
uniform float3 secondaryColor;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    
    // Start with background color
    float3 finalColor = backgroundColor;
    
    // Normalize coordinates to -1 to 1 for better wave calculations
    float2 normalizedUV = (uv - 0.5) * 2.0;
    
    // Base gradient using theme colors
    float3 baseColor = mix(
        primaryColor * 0.8,      // Slightly darker primary
        secondaryColor,          // Secondary color from theme
        uv.y
    );
    
    // Secondary gradient for depth using theme colors
    float3 depthColor = mix(
        primaryColor * 0.6,      // Darker primary
        secondaryColor * 0.9,    // Slightly darker secondary
        uv.y * uv.y
    );
    
    // Multiple wave layers for liquid effect (MUCH slower for smoothness)
    float wave1 = sin(uv.x * 3.0 + time * 0.05) * 0.08 * amplitude;
    float wave2 = sin(uv.x * 5.0 - time * 0.03) * 0.06 * amplitude;
    float wave3 = sin(uv.x * 2.0 + time * 0.02) * 0.1 * amplitude;
    float wave4 = cos(uv.x * 4.0 + time * 0.04) * 0.05 * amplitude;
    
    // Combine waves for complex liquid motion with more layers
    float combinedWave = wave1 + wave2 + wave3 + wave4;
    
    // Organic distortion using noise-like functions (ultra slow)
    float distortion = sin(uv.x * 10.0 + time * 0.02) * cos(uv.y * 8.0 + time * 0.015) * 0.02 * amplitude;
    float distortion2 = sin(uv.x * 6.0 - time * 0.01) * sin(uv.y * 12.0 + time * 0.025) * 0.015 * amplitude;
    
    // Final wave position - positioned lower for background effect
    float waveY = 0.7 + combinedWave + distortion + distortion2;
    
    // Create liquid mask with VERY smooth falloff for blur effect
    float liquidMask = smoothstep(waveY - 0.15, waveY + 0.15, uv.y);
    float liquidMask2 = smoothstep(waveY - 0.3, waveY + 0.3, uv.y) * 0.5;
    
    // Multiple glow layers for extreme blur effect
    float glowDistance = abs(uv.y - waveY);
    float innerGlow = exp(-glowDistance * 20.0 * (2.0 - amplitude)) * intensity;
    float midGlow = exp(-glowDistance * 10.0 * (2.0 - amplitude)) * intensity * 0.7;
    float outerGlow = exp(-glowDistance * 5.0 * (2.0 - amplitude)) * intensity * 0.4;
    float superOuterGlow = exp(-glowDistance * 2.0 * (2.0 - amplitude)) * intensity * 0.2;
    
    // Surface highlights that move with the wave (ultra slow and blurred)
    float highlight = exp(-abs(uv.y - (waveY + 0.03)) * 40.0) * intensity * 0.3;
    highlight *= sin(uv.x * 15.0 + time * 0.08) * 0.5 + 0.5; // Much slower sparkles
    
    // Combine colors with multiple blur layers (start from backgroundColor)
    float3 waveColor = baseColor * liquidMask;
    waveColor += baseColor * liquidMask2; // Extra blur layer
    waveColor += depthColor * innerGlow;
    waveColor += depthColor * midGlow * 0.5; // Mid blur
    waveColor += baseColor * outerGlow;
    waveColor += baseColor * superOuterGlow; // Super wide blur
    waveColor += float3(1.0, 1.0, 1.0) * highlight * 0.5; // Softer highlights
    
    // Blend wave effect with background
    float totalEffect = liquidMask + liquidMask2 + innerGlow + midGlow + outerGlow + superOuterGlow;
    totalEffect = clamp(totalEffect, 0.0, 1.0);
    finalColor = mix(backgroundColor, waveColor, totalEffect);
    
    // Add VERY subtle color variation (ultra slow for smoothness)
    finalColor.r += sin(uv.x * 5.0 + time * 0.03) * 0.05 * intensity;
    finalColor.g += cos(uv.x * 4.0 - time * 0.025) * 0.05 * intensity;
    finalColor.b += sin(uv.x * 6.0 + time * 0.02) * 0.05 * intensity;
    
    // Additional color blending for fusion effect
    float blendFactor = sin(time * 0.01) * 0.5 + 0.5;
    finalColor = mix(finalColor, finalColor.bgr, blendFactor * 0.2);
    
    // Ensure colors stay in valid range
    finalColor = clamp(finalColor, 0.0, 1.0);
    
    return half4(finalColor, 1.0);
}