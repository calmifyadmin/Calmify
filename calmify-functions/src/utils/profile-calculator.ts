/**
 * Profile Calculator Utilities
 * Week 6: PSYCHOLOGICAL_INSIGHTS_PLAN.md
 *
 * Statistical functions for computing psychological profiles
 */

/**
 * Trend enum (matches Android model)
 */
export enum Trend {
  IMPROVING = "IMPROVING",
  STABLE = "STABLE",
  DECLINING = "DECLINING",
  INSUFFICIENT_DATA = "INSUFFICIENT_DATA",
}

/**
 * Stress Peak interface
 */
export interface StressPeak {
  timestamp: number; // Unix timestamp (milliseconds)
  level: number; // Stress level (0-10)
  trigger: string | null; // Trigger type
  resolved: boolean; // Has stress returned to baseline?
}

/**
 * Time-series data point
 */
export interface DataPoint {
  value: number;
  timestamp: number; // Unix timestamp
}

/**
 * Calculate weighted average with exponential decay
 * More recent values have higher weight
 *
 * @param values Array of data points (sorted oldest to newest)
 * @param decayFactor Decay factor (default 0.8 = 20% decay per step)
 * @return Weighted average
 */
export function calculateWeightedAverage(
  values: DataPoint[],
  decayFactor = 0.8
): number {
  if (values.length === 0) return 0;
  if (values.length === 1) return values[0].value;

  let weightedSum = 0;
  let weightSum = 0;

  // Exponential decay: most recent = weight 1.0, previous = 0.8, etc.
  for (let i = 0; i < values.length; i++) {
    const weight = Math.pow(decayFactor, values.length - 1 - i);
    weightedSum += values[i].value * weight;
    weightSum += weight;
  }

  return weightSum > 0 ? weightedSum / weightSum : 0;
}

/**
 * Calculate standard deviation (volatility)
 *
 * @param values Array of numeric values
 * @return Standard deviation
 */
export function calculateStdDev(values: number[]): number {
  if (values.length === 0) return 0;
  if (values.length === 1) return 0;

  const mean = values.reduce((sum, val) => sum + val, 0) / values.length;
  const squaredDiffs = values.map((val) => Math.pow(val - mean, 2));
  const variance =
    squaredDiffs.reduce((sum, val) => sum + val, 0) / values.length;

  return Math.sqrt(variance);
}

/**
 * Detect stress peaks above threshold
 *
 * @param dataPoints Array of data points
 * @param threshold Stress level threshold (default 7)
 * @param baseline Baseline stress level for resolved detection
 * @return Array of stress peaks
 */
export function detectPeaks(
  dataPoints: DataPoint[],
  threshold = 7,
  baseline = 5
): StressPeak[] {
  const peaks: StressPeak[] = [];

  for (let i = 0; i < dataPoints.length; i++) {
    const point = dataPoints[i];

    if (point.value >= threshold) {
      // Check if stress returned to baseline after this peak
      let resolved = false;
      for (let j = i + 1; j < dataPoints.length; j++) {
        if (dataPoints[j].value <= baseline + 1) {
          resolved = true;
          break;
        }
      }

      peaks.push({
        timestamp: point.timestamp,
        level: Math.round(point.value),
        trigger: null, // Will be enriched from diary data if available
        resolved,
      });
    }
  }

  return peaks;
}

/**
 * Calculate resilience index (recovery time after stress peaks)
 * Higher score = faster recovery
 *
 * @param dataPoints Array of stress data points
 * @param peaks Array of detected stress peaks
 * @return Resilience index (0-1)
 */
export function calculateResilience(
  dataPoints: DataPoint[],
  peaks: StressPeak[]
): number {
  if (peaks.length === 0) return 0.5; // Neutral (no peaks to recover from)

  const baseline = calculateWeightedAverage(dataPoints);
  let totalRecoveryTime = 0;
  let recoveredPeaksCount = 0;

  for (const peak of peaks) {
    if (!peak.resolved) continue;

    // Find peak index
    const peakIndex = dataPoints.findIndex((p) => p.timestamp === peak.timestamp);
    if (peakIndex === -1) continue;

    // Find when stress returned to baseline
    let recoveryIndex = -1;
    for (let i = peakIndex + 1; i < dataPoints.length; i++) {
      if (dataPoints[i].value <= baseline + 1) {
        recoveryIndex = i;
        break;
      }
    }

    if (recoveryIndex !== -1) {
      const recoveryTimeMs =
        dataPoints[recoveryIndex].timestamp - dataPoints[peakIndex].timestamp;
      const recoveryDays = recoveryTimeMs / (1000 * 60 * 60 * 24);
      totalRecoveryTime += recoveryDays;
      recoveredPeaksCount++;
    }
  }

  if (recoveredPeaksCount === 0) return 0.3; // Had peaks but no recovery

  // Average recovery time in days
  const avgRecoveryDays = totalRecoveryTime / recoveredPeaksCount;

  // Convert to resilience index (0-1)
  // Fast recovery (< 1 day) = high resilience (0.9+)
  // Slow recovery (> 7 days) = low resilience (< 0.3)
  const resilienceIndex = Math.max(0, Math.min(1, 1 - avgRecoveryDays / 7));

  return resilienceIndex;
}

/**
 * Detect trend in time-series data
 * Uses linear regression slope
 *
 * @param dataPoints Array of data points (sorted oldest to newest)
 * @param improvingThreshold Positive slope threshold (default 0.1)
 * @param decliningThreshold Negative slope threshold (default -0.1)
 * @return Trend enum
 */
export function detectTrend(
  dataPoints: DataPoint[],
  improvingThreshold = 0.1,
  decliningThreshold = -0.1
): Trend {
  if (dataPoints.length < 3) return Trend.INSUFFICIENT_DATA;

  // Normalize timestamps to days from first point
  const firstTimestamp = dataPoints[0].timestamp;
  const normalizedPoints = dataPoints.map((p) => ({
    x: (p.timestamp - firstTimestamp) / (1000 * 60 * 60 * 24), // Days
    y: p.value,
  }));

  // Calculate linear regression slope
  const n = normalizedPoints.length;
  const sumX = normalizedPoints.reduce((sum, p) => sum + p.x, 0);
  const sumY = normalizedPoints.reduce((sum, p) => sum + p.y, 0);
  const sumXY = normalizedPoints.reduce((sum, p) => sum + p.x * p.y, 0);
  const sumX2 = normalizedPoints.reduce((sum, p) => sum + p.x * p.x, 0);

  const slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

  // Determine trend based on slope
  if (slope > improvingThreshold) {
    return Trend.IMPROVING;
  } else if (slope < decliningThreshold) {
    return Trend.DECLINING;
  } else {
    return Trend.STABLE;
  }
}

/**
 * Calculate confidence score based on data density
 * More data = higher confidence
 *
 * @param diaryCount Number of diary entries in period
 * @param snapshotCount Number of wellbeing snapshots in period
 * @param periodDays Number of days in period (default 7)
 * @return Confidence score (0-1)
 */
export function calculateConfidence(
  diaryCount: number,
  snapshotCount: number,
  periodDays = 7
): number {
  // Ideal: 7 diaries (1/day) + 1 snapshot/week
  const idealDiaryCount = periodDays;
  const idealSnapshotCount = 1;

  const diaryScore = Math.min(diaryCount / idealDiaryCount, 1.0);
  const snapshotScore = snapshotCount >= idealSnapshotCount ? 1.0 : snapshotCount;

  // Weighted average (diaries 70%, snapshots 30%)
  const confidence = diaryScore * 0.7 + snapshotScore * 0.3;

  return Math.round(confidence * 100) / 100; // Round to 2 decimals
}

/**
 * Calculate mood baseline from mood strings
 * Maps mood names to numeric values
 *
 * @param moods Array of mood strings (e.g., ["Happy", "Neutral", "Sad"])
 * @return Average mood value (0-10)
 */
export function calculateMoodBaseline(moods: string[]): number {
  if (moods.length === 0) return 5; // Neutral default

  const moodValues: {[key: string]: number} = {
    // Positive moods
    Happy: 8,
    Joyful: 9,
    Excited: 8.5,
    Content: 7,
    Peaceful: 7.5,
    Grateful: 8,

    // Neutral moods
    Neutral: 5,
    Calm: 6,
    Okay: 5,

    // Negative moods
    Sad: 3,
    Anxious: 2.5,
    Angry: 2,
    Frustrated: 3,
    Depressed: 1.5,
    Lonely: 2,
    Stressed: 2.5,

    // Default for unknown moods
    Unknown: 5,
  };

  const numericMoods = moods.map((mood) => moodValues[mood] || 5);
  const sum = numericMoods.reduce((acc, val) => acc + val, 0);

  return sum / numericMoods.length;
}
