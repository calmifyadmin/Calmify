const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');
const textToSpeech = require('@google-cloud/text-to-speech');

// Inizializzazione
const app = express();
const PORT = process.env.PORT || 8080;

// Middleware
app.use(cors());
app.use(express.json({ limit: '10mb' }));

// Inizializza Firebase Admin
if (process.env.NODE_ENV === 'production') {
  admin.initializeApp({
    databaseURL: "https://calmify-388723-default-rtdb.europe-west1.firebasedatabase.app"
  });
} else {
  const serviceAccount = require('./calmify-388723-205b5fd223e4.json');
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: "https://calmify-388723-default-rtdb.europe-west1.firebasedatabase.app"
  });
}

const db = admin.database();
const ttsClient = new textToSpeech.TextToSpeechClient();

// Sistema di Conversazione Naturale
class ConversationalSpeechEngine {
  constructor() {
    this.emotionalMemory = new Map();
    this.breathingModel = new RealisticBreathingModel();
  }

  analyzeEmotionalProgression(text, previousMessages = []) {
    const emotions = [];
    const segments = this.detectThoughtUnits(text);

    segments.forEach((segment, index) => {
      const emotion = this.detectSegmentEmotion(segment.text, previousMessages);
      const intensity = this.calculateEmotionalIntensity(segment.text);

      emotions.push({
        emotion,
        intensity,
        startIndex: segment.startIndex,
        endIndex: segment.endIndex
      });
    });

    return emotions;
  }

  detectThoughtUnits(text) {
    const units = [];
    const sentences = text.match(/[^.!?]+[.!?]+/g) || [text];
    let currentIndex = 0;

    sentences.forEach(sentence => {
      // Suddividi ulteriormente per clausole
      const clauses = sentence.split(/,(?=\s)|\s(?:ma|però|quindi|inoltre|comunque)\s/gi);

      clauses.forEach(clause => {
        if (clause.trim().length > 0) {
          units.push({
            text: clause.trim(),
            startIndex: currentIndex,
            endIndex: currentIndex + clause.length,
            requiresReflection: this.isReflectiveContent(clause),
            hasEmphasis: this.detectEmphasis(clause)
          });
          currentIndex += clause.length;
        }
      });
    });

    return units;
  }

  detectSegmentEmotion(text, context = []) {
    const lowercaseText = text.toLowerCase();

    // Analisi delle parole chiave emotive
    const emotionKeywords = {
      excited: ['fantastico', 'incredibile', 'wow', 'straordinario', 'meraviglioso'],
      happy: ['felice', 'contento', 'gioia', 'bene', 'ottimo'],
      sad: ['triste', 'dispiaciuto', 'purtroppo', 'male', 'difficile'],
      thoughtful: ['penso', 'credo', 'forse', 'probabilmente', 'riflettere'],
      emphatic: ['importante', 'fondamentale', 'essenziale', 'davvero', 'assolutamente']
    };

    for (const [emotion, keywords] of Object.entries(emotionKeywords)) {
      if (keywords.some(keyword => lowercaseText.includes(keyword))) {
        return emotion;
      }
    }

    // Analisi della punteggiatura
    if (text.includes('!')) return 'excited';
    if (text.includes('?')) return 'curious';
    if (text.includes('...')) return 'thoughtful';

    return 'neutral';
  }

  calculateEmotionalIntensity(text) {
    let intensity = 0.5; // Base neutrale

    // Punteggiatura multipla aumenta l'intensità
    const exclamationCount = (text.match(/!/g) || []).length;
    const questionCount = (text.match(/\?/g) || []).length;

    intensity += exclamationCount * 0.15;
    intensity += questionCount * 0.1;

    // Parole in maiuscolo
    const uppercaseWords = text.match(/\b[A-Z]{2,}\b/g) || [];
    intensity += uppercaseWords.length * 0.1;

    // Lunghezza della frase (frasi brevi tendono ad essere più intense)
    if (text.length < 30) intensity += 0.1;

    return Math.min(1.0, Math.max(0.1, intensity));
  }

  isReflectiveContent(text) {
    const reflectivePatterns = [
      /\bpenso che\b/i,
      /\bcredo che\b/i,
      /\bforse\b/i,
      /\bprobabilmente\b/i,
      /\bmi chiedo\b/i,
      /\bnon sono sicuro\b/i
    ];

    return reflectivePatterns.some(pattern => pattern.test(text));
  }

  detectEmphasis(text) {
    const emphasisPatterns = [
      /\b(molto|davvero|proprio|assolutamente)\b/i,
      /\b(importante|fondamentale|essenziale)\b/i,
      /[A-Z]{2,}/,
      /!+/
    ];

    return emphasisPatterns.some(pattern => pattern.test(text));
  }
}

class RealisticBreathingModel {
  constructor() {
    this.lungCapacity = 100;
    this.currentCapacity = 100;
  }

  calculateBreathingPoints(text, emotionalState) {
    const breathingPoints = [];
    const words = text.split(' ');
    let wordsSinceBreath = 0;
    let charactersSinceBreath = 0;

    words.forEach((word, index) => {
      wordsSinceBreath++;
      charactersSinceBreath += word.length + 1;

      // Consumo di aria basato su emozione
      const airConsumption = this.calculateAirConsumption(word, emotionalState);
      this.currentCapacity -= airConsumption;

      // Condizioni per respirare
      const needsBreath =
        this.currentCapacity < 20 ||
        (this.currentCapacity < 40 && word.match(/[.!?]/)) ||
        wordsSinceBreath > 15 ||
        charactersSinceBreath > 80 ||
        (emotionalState === 'excited' && this.currentCapacity < 50);

      if (needsBreath) {
        const breathType = this.determineBreathType(this.currentCapacity, emotionalState);
        breathingPoints.push({
          position: index,
          type: breathType,
          duration: this.getBreathDuration(breathType)
        });

        this.currentCapacity = breathType === 'deep' ? 100 : 80;
        wordsSinceBreath = 0;
        charactersSinceBreath = 0;
      }
    });

    return breathingPoints;
  }

  calculateAirConsumption(word, emotionalState) {
    let consumption = word.length * 0.5;

    if (emotionalState === 'excited') consumption *= 1.3;
    if (emotionalState === 'emphatic') consumption *= 1.2;
    if (word.match(/[!?]/)) consumption *= 1.1;

    return consumption;
  }

  determineBreathType(remainingCapacity, emotionalState) {
    if (remainingCapacity < 10) return 'deep';
    if (emotionalState === 'excited' && remainingCapacity < 30) return 'quick';
    if (emotionalState === 'thoughtful') return 'normal';
    return 'normal';
  }

  getBreathDuration(type) {
    const durations = {
      quick: 200,
      normal: 400,
      deep: 600,
      sigh: 800
    };
    return durations[type] || 400;
  }

  insertBreath(type) {
    const breathConfigs = {
      quick: '<break time="200ms"/>',
      normal: '<break time="400ms"/>',
      deep: '<break time="600ms"/>',
      sigh: '<break time="800ms"/><prosody pitch="-20%"><break time="200ms"/></prosody>'
    };

    return breathConfigs[type] || breathConfigs.normal;
  }
}

// Funzione principale per costruire SSML avanzato
// Update the SSML builder to add more quality enhancements for maximum quality
function buildAdvancedSSML(text, options) {
  const { emotion, isFirst, isLast, quality } = options;
  const speechEngine = new ConversationalSpeechEngine();
  const breathingModel = new RealisticBreathingModel();

  // Analisi emotiva del testo
  const emotionalFlow = speechEngine.analyzeEmotionalProgression(text);
  const thoughtSegments = speechEngine.detectThoughtUnits(text);
  const breathingPoints = breathingModel.calculateBreathingPoints(text, emotion);

  let ssml = '<speak>';

  // For maximum quality, add more natural elements
  if (quality === 'MAXIMUM') {
    // Add a subtle ambient pause at the beginning
    if (isFirst) {
      ssml += '<break time="200ms"/>';
    }

    // Add prosody wrapper for overall quality
    ssml += '<prosody rate="98%" pitch="+0st">';
  }

  let currentWordIndex = 0;

  thoughtSegments.forEach((segment, segmentIndex) => {
    const segmentEmotion = emotionalFlow.find(e =>
      e.startIndex <= segment.startIndex && e.endIndex >= segment.endIndex
    ) || { emotion: 'neutral', intensity: 0.5 };

    // Respiro iniziale se necessario
    if (segmentIndex === 0 && !isFirst) {
      ssml += '<break time="300ms"/>';
    } else if (segment.text.length > 50) {
      ssml += breathingModel.insertBreath('normal');
    }

    // Processa ogni parola del segmento
    const words = segment.text.split(' ');

    words.forEach((word, wordIndex) => {
      // Controlla se è necessario un respiro
      const breathPoint = breathingPoints.find(bp => bp.position === currentWordIndex);
      if (breathPoint) {
        ssml += breathingModel.insertBreath(breathPoint.type);
      }

      // Calcola variazioni dinamiche per la parola
      const wordSpeed = calculateWordSpeed(word, segmentEmotion, wordIndex, words.length);
      const wordPitch = calculateWordPitch(word, segmentEmotion, wordIndex);

      // For maximum quality, add more subtle variations
      if (quality === 'MAXIMUM') {
        // Add micro-timing variations
        if (Math.random() < 0.05) {
          ssml += '<break time="10ms"/>';
        }
      }

      // Gestione speciale per parole enfatiche
      if (isEmphaticWord(word)) {
        ssml += `<prosody rate="${wordSpeed * 0.85}" pitch="${wordPitch}+10%">`;
        ssml += `<emphasis level="moderate">${word}</emphasis>`;
        ssml += '</prosody>';
      } else if (isTransitionWord(word)) {
        // Esitazione naturale su parole di transizione
        ssml += `<break time="100ms"/>`;
        ssml += `<prosody rate="${wordSpeed * 0.9}" pitch="${wordPitch}">`;
        ssml += word;
        ssml += '</prosody>';
      } else {
        // Parola normale con variazioni naturali
        ssml += `<prosody rate="${wordSpeed}" pitch="${wordPitch}">`;
        ssml += word;
        ssml += '</prosody>';
      }

      // Micro-pause naturali
      if (shouldInsertMicroPause(word, wordIndex, words)) {
        const pauseDuration = calculateMicroPauseDuration(word, segmentEmotion);
        ssml += `<break time="${pauseDuration}ms"/>`;
      } else {
        ssml += ' ';
      }

      currentWordIndex++;
    });

    // Pausa tra segmenti di pensiero
    if (segmentIndex < thoughtSegments.length - 1) {
      const pauseLength = calculateInterSegmentPause(segment, segmentEmotion);
      ssml += `<break time="${pauseLength}ms"/>`;
    }
  });

  // Close prosody wrapper for maximum quality
  if (quality === 'MAXIMUM') {
    ssml += '</prosody>';
  }

  // Pausa finale se non è l'ultimo chunk
  if (!isLast) {
    ssml += '<break time="200ms"/>';
  }

  ssml += '</speak>';
  return ssml;
}

function calculateWordSpeed(word, emotion, position, totalWords) {
  let baseSpeed = 1.0;

  // Velocità base per emozione
  const emotionSpeeds = {
    excited: 1.15,
    happy: 1.05,
    sad: 0.9,
    thoughtful: 0.85,
    emphatic: 0.95,
    neutral: 1.0
  };

  baseSpeed = emotionSpeeds[emotion.emotion] || 1.0;

  // Variazione basata sulla posizione nella frase
  if (position < 2) {
    // Accelerazione all'inizio
    baseSpeed *= 1.2;
  } else if (position > totalWords - 3) {
    // Rallentamento alla fine
    baseSpeed *= 1.0;
  }

  // Variazione per intensità emotiva
  baseSpeed *= (1.0 + emotion.intensity * 0.2);

  // Aggiungi variazione casuale naturale
  const randomVariation = 1.1 + Math.random() * 0.1;
  baseSpeed *= randomVariation;

  return baseSpeed.toFixed(2);
}

function calculateWordPitch(word, emotion, position) {
  let basePitch = '+0%';

  // Pitch base per emozione
  const emotionPitches = {
    excited: '+10%',
    happy: '+5%',
    sad: '-5%',
    thoughtful: '-2%',
    emphatic: '+8%',
    neutral: '+0%'
  };

  const emotionPitch = parseInt(emotionPitches[emotion.emotion] || '0');

  // Variazione per domande
  if (word.includes('?')) {
    return `+${emotionPitch + 15}%`;
  }

  // Variazione per enfasi
  if (word.match(/[A-Z]{2,}/) || word.includes('!')) {
    return `+${emotionPitch + 10}%`;
  }

  // Piccola variazione casuale
  const randomVariation = -2 + Math.random() * 4;
  const finalPitch = emotionPitch + randomVariation;

  return finalPitch >= 0 ? `+${finalPitch.toFixed(0)}%` : `${finalPitch.toFixed(0)}%`;
}

function isEmphaticWord(word) {
  const emphaticWords = [
    'importante', 'fondamentale', 'essenziale', 'davvero',
    'assolutamente', 'proprio', 'molto', 'sempre', 'mai'
  ];

  return emphaticWords.some(ew => word.toLowerCase().includes(ew));
}

function isTransitionWord(word) {
  const transitions = ['ma', 'però', 'quindi', 'allora', 'comunque', 'infatti'];
  return transitions.includes(word.toLowerCase());
}

function shouldInsertMicroPause(word, position, words) {
  // Pausa dopo virgola
  if (word.includes(',')) return true;

  // Pausa prima di congiunzioni
  if (position < words.length - 1 && isTransitionWord(words[position + 1])) return true;

  // Pausa occasionale per naturalezza (10% di probabilità)
  return Math.random() < 0.1;
}

function calculateMicroPauseDuration(word, emotion) {
  let baseDuration = 50;

  if (word.includes(',')) baseDuration = 150;
  if (word.includes(':') || word.includes(';')) baseDuration = 200;

  // Emozioni riflessive hanno pause più lunghe
  if (emotion.emotion === 'thoughtful') baseDuration *= 1.5;

  // Aggiungi variazione casuale
  return baseDuration + Math.random() * 50;
}

function calculateInterSegmentPause(segment, emotion) {
  let basePause = 300;

  if (segment.text.endsWith('.')) basePause = 400;
  if (segment.text.endsWith('!')) basePause = 350;
  if (segment.text.endsWith('?')) basePause = 450;

  // Pause più lunghe per contenuto riflessivo
  if (segment.requiresReflection) basePause *= 1.3;

  return basePause;
}

// Funzione di chunking intelligente ottimizzata
function intelligentChunking(text, chunkDurationMs) {
  const naturalBreakpoints = findNaturalBreakpoints(text);
  const chunks = [];
  let currentChunk = '';
  let lastBreakpoint = 0;

  // Stima approssimativa: 150 parole al minuto = 2.5 parole al secondo
  const wordsPerSecond = 2.5;
  const targetWordsPerChunk = Math.floor((chunkDurationMs / 1000) * wordsPerSecond);

  naturalBreakpoints.forEach((breakpoint, index) => {
    const segment = text.substring(lastBreakpoint, breakpoint).trim();
    const currentWords = currentChunk.split(' ').filter(w => w.length > 0).length;
    const segmentWords = segment.split(' ').filter(w => w.length > 0).length;

    // Decide se aggiungere al chunk corrente o crearne uno nuovo
    if (currentWords + segmentWords <= targetWordsPerChunk * 1.5) {
      currentChunk += (currentChunk ? ' ' : '') + segment;
    } else {
      if (currentChunk) {
        chunks.push(currentChunk.trim());
      }
      currentChunk = segment;
    }

    lastBreakpoint = breakpoint;
  });

  // Aggiungi l'ultimo chunk
  if (currentChunk) {
    chunks.push(currentChunk.trim());
  }

  // Se non ci sono breakpoint naturali, dividi per lunghezza
  if (chunks.length === 0) {
    const words = text.split(' ');
    for (let i = 0; i < words.length; i += targetWordsPerChunk) {
      chunks.push(words.slice(i, i + targetWordsPerChunk).join(' '));
    }
  }

  console.log(`📝 Natural chunking: ${text.length} chars -> ${chunks.length} chunks`);
  chunks.forEach((chunk, i) => {
    console.log(`   Chunk ${i}: ${chunk.substring(0, 50)}... (${chunk.split(' ').length} words)`);
  });

  return chunks;
}

function findNaturalBreakpoints(text) {
  const breakpoints = new Set();

  // Pattern per identificare punti di interruzione naturali
  const patterns = [
    { regex: /[.!?]\s+/g, priority: 1 },      // Fine frase
    { regex: /[;]\s+/g, priority: 2 },        // Punto e virgola
    { regex: /,\s+(?=ma|però|quindi|inoltre|comunque|tuttavia)/gi, priority: 3 }, // Virgola prima di congiunzioni
    { regex: /:\s+/g, priority: 4 },          // Due punti
    { regex: /\s+[-–—]\s+/g, priority: 5 },   // Trattini
    { regex: /,\s+/g, priority: 6 }           // Qualsiasi virgola
  ];

  patterns.forEach(({ regex, priority }) => {
    let match;
    while ((match = regex.exec(text)) !== null) {
      breakpoints.add({
        position: match.index + match[0].length,
        priority: priority
      });
    }
  });

  // Converti in array ordinato e rimuovi duplicati vicini
  const sortedBreakpoints = Array.from(breakpoints)
    .sort((a, b) => a.position - b.position)
    .filter((bp, index, arr) => {
      if (index === 0) return true;
      return bp.position - arr[index - 1].position > 10; // Almeno 10 caratteri di distanza
    })
    .map(bp => bp.position);

  // Aggiungi la fine del testo
  if (sortedBreakpoints.length === 0 || sortedBreakpoints[sortedBreakpoints.length - 1] < text.length) {
    sortedBreakpoints.push(text.length);
  }

  return sortedBreakpoints;
}

// Health check endpoint
app.get('/', (req, res) => {
  res.json({
    status: 'healthy',
    service: 'audio-streaming-api',
    version: '2.0.0',
    features: ['natural-voice', 'emotional-synthesis', 'breathing-model']
  });
});

// Test endpoint
app.get('/test', (req, res) => {
  res.json({
    status: 'OK',
    message: 'Natural Voice Audio Streaming API',
    timestamp: new Date().toISOString()
  });
});

// Endpoint principale con sistema di voce naturale
app.post('/generateNeuralAudio', async (req, res) => {
  try {
    console.log('🎙️ Natural voice request received');

    const authToken = req.headers.authorization?.split('Bearer ')[1];
    if (!authToken) {
      return res.status(401).json({ error: 'Token di autenticazione mancante' });
    }

    let decodedToken;
    try {
      decodedToken = await admin.auth().verifyIdToken(authToken);
      console.log('✅ Token verified for user:', decodedToken.uid);
    } catch (authError) {
      return res.status(401).json({ error: 'Token non valido: ' + authError.message });
    }

    const userId = decodedToken.uid;
    const {
      messageId,
      text,
      voice = {
        languageCode: 'it-IT',
        name: 'it-IT-Neural2-A', // Female voice
        ssmlGender: 'FEMALE'
      },
      audioConfig = {
        audioEncoding: 'LINEAR16',
        sampleRateHertz: 48000,  // Default to high quality
        audioChannelCount: 2      // Default to stereo
      },
      streaming = {
        chunkDurationMs: 150,     // Longer chunks for quality
        overlapMs: 20
      },
      emotion = 'NEUTRAL',
      conversationHistory = [],
      quality = 'STANDARD'        // Can be STANDARD or MAXIMUM
    } = req.body;

    if (!messageId || !text) {
      return res.status(400).json({ error: 'messageId e text sono obbligatori' });
    }

    // Override settings for MAXIMUM quality
    if (quality === 'MAXIMUM') {
      audioConfig.sampleRateHertz = 48000;
      audioConfig.audioChannelCount = 2;
      streaming.chunkDurationMs = 150;
      streaming.overlapMs = 20;
    }

    console.log('🎯 Processing HIGH QUALITY speech:', {
      messageId,
      textLength: text.length,
      emotion,
      voiceName: voice.name,
      sampleRate: audioConfig.sampleRateHertz,
      channels: audioConfig.audioChannelCount,
      quality,
      userId
    });

    // Chunking intelligente con breakpoint naturali
    const chunks = intelligentChunking(text, streaming.chunkDurationMs);
    console.log(`📝 Natural chunking completed: ${chunks.length} chunks`);

    // Genera audio per ogni chunk con voce naturale
    const streamRef = db.ref(`audio_streams/${messageId}/chunks`);
    let processedChunks = 0;
    const errors = [];

    // Process chunks sequentially to maintain order
    for (let index = 0; index < chunks.length; index++) {
      const chunk = chunks[index];

      try {
        // Costruisci SSML avanzato con respirazione e prosodia naturale
        const ssml = buildAdvancedSSML(chunk, {
          emotion,
          isFirst: index === 0,
          isLast: index === chunks.length - 1,
          overlap: streaming.overlapMs,
          conversationHistory: Array.isArray(conversationHistory) ? conversationHistory : [],
          quality
        });

        console.log(`🎵 Generating HIGH QUALITY audio for chunk ${index + 1}/${chunks.length}`);
        console.log(`   Text preview: "${chunk.substring(0, 50)}..."`);

        // Configurazione TTS ottimizzata per qualità massima
        const ttsRequest = {
          input: { ssml },
          voice: {
            languageCode: voice.languageCode,
            name: voice.name || 'it-IT-Neural2-A',
            ssmlGender: voice.ssmlGender || 'FEMALE'
          },
          audioConfig: {
            audioEncoding: 'LINEAR16',
            sampleRateHertz: audioConfig.sampleRateHertz || 48000,
            // Google TTS doesn't directly support stereo, but we can request effects
            effectsProfileId: quality === 'MAXIMUM'
              ? ['large-home-entertainment-class-device', 'headphone-class-device']
              : ['headphone-class-device'],
            pitch: 0,
            speakingRate: 1.0,
            volumeGainDb: quality === 'MAXIMUM' ? 3 : 0  // Slight boost for maximum quality
          }
        };

        console.log(`   Voice: ${ttsRequest.voice.name} (${ttsRequest.voice.ssmlGender})`);
        console.log(`   Audio config:`, ttsRequest.audioConfig);
        console.log(`   Quality mode: ${quality}`);

        const [response] = await ttsClient.synthesizeSpeech(ttsRequest);

        // Log the audio data size
        const audioSize = response.audioContent.length;
        console.log(`   Generated ${audioSize} bytes of audio data`);
        console.log(`   Estimated duration: ${(audioSize / (audioConfig.sampleRateHertz * 2)) * 1000}ms`);

        // For maximum quality, we'll duplicate mono to stereo in post-processing
        // since Google TTS outputs mono, but we tell the client it's ready for stereo
        const isRequestingStereo = audioConfig.audioChannelCount === 2;

        // Save to Firebase with enhanced metadata
        const chunkRef = streamRef.child(`chunk_${index}`);
        const chunkData = {
          id: `chunk_${index}`,
          data: response.audioContent.toString('base64'),
          dataSize: audioSize,
          emotion: emotion,
          naturalness: {
            hasBreathing: true,
            hasProsody: true,
            hasEmphasis: true,
            spatialMovement: quality === 'MAXIMUM'
          },
          sequence: index,
          timestamp: Date.now() + (index * streaming.chunkDurationMs),
          isLast: index === chunks.length - 1,
          userId: userId,
          encoding: 'LINEAR16',
          sampleRate: audioConfig.sampleRateHertz,
          channels: isRequestingStereo ? 2 : 1,  // Tell client to expect stereo if requested
          quality: quality,
          requiresStereoConversion: isRequestingStereo  // Client will convert mono to stereo
        };

        await chunkRef.set(chunkData);

        processedChunks++;
        console.log(`✅ HIGH QUALITY chunk ${index + 1}/${chunks.length} saved to Firebase`);
        console.log(`   Firebase path: audio_streams/${messageId}/chunks/chunk_${index}`);

      } catch (ttsError) {
        console.error(`❌ TTS error for chunk ${index}:`, ttsError);
        errors.push({
          chunk: index,
          error: ttsError.message
        });
      }

      // Small delay between chunks to avoid rate limiting
      if (index < chunks.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 30));
      }
    }

    // Save metadata
    await db.ref(`audio_streams/${messageId}/metadata`).set({
      messageId,
      userId,
      totalChunks: chunks.length,
      processedChunks,
      emotion,
      naturalVoice: true,
      voiceName: voice.name,
      sampleRate: audioConfig.sampleRateHertz,
      channels: audioConfig.audioChannelCount,
      quality,
      createdAt: admin.database.ServerValue.TIMESTAMP,
      status: processedChunks === chunks.length ? 'completed' : 'partial',
      errors: errors.length > 0 ? errors : null
    });

    console.log('🎉 HIGH QUALITY voice processing completed:', {
      totalChunks: chunks.length,
      processedChunks,
      errors: errors.length,
      quality,
      sampleRate: audioConfig.sampleRateHertz + 'Hz',
      channels: audioConfig.audioChannelCount
    });

    res.json({
      success: true,
      chunks: chunks.length,
      processedChunks,
      messageId,
      naturalVoice: true,
      quality,
      audioSpecs: {
        sampleRate: audioConfig.sampleRateHertz,
        channels: audioConfig.audioChannelCount,
        encoding: 'LINEAR16'
      },
      errors: errors.length > 0 ? errors : undefined
    });

  } catch (error) {
    console.error('💥 General error:', error);
    res.status(500).json({
      error: error.message,
      stack: process.env.NODE_ENV === 'development' ? error.stack : undefined
    });
  }
});

// Endpoint legacy per retrocompatibilità
app.post('/audio-streaming-api', async (req, res) => {
  console.log('⚠️ Legacy endpoint called, redirecting to /generateNeuralAudio');
  req.url = '/generateNeuralAudio';
  app.handle(req, res);
});

// Start server
const server = app.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 Natural Voice Server listening on port ${PORT}`);
  console.log(`🎙️ Features enabled:`);
  console.log(`   - Natural speech synthesis`);
  console.log(`   - Emotional prosody`);
  console.log(`   - Realistic breathing`);
  console.log(`   - Dynamic rhythm variation`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM received, closing server...');
  server.close(() => {
    console.log('Server closed');
    process.exit(0);
  });
});

process.on('uncaughtException', (error) => {
  console.error('Uncaught Exception:', error);
  process.exit(1);
});

process.on('unhandledRejection', (error) => {
  console.error('Unhandled Rejection:', error);
  process.exit(1);
});