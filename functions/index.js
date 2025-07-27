// All'inizio del file aggiungi:
const express = require('express');
const app = express();
const PORT = process.env.PORT || 8080;

app.use(express.json());

// Converti la tua function in endpoint
app.post('/generateNeuralAudio', async (req, res) => {
  try {
    const data = req.body;
    const context = { auth: { uid: req.headers['x-user-id'] } };
    
    // Chiama la tua logica esistente
    const result = await generateNeuralAudioLogic(data, context);
    
    res.json(result);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Health check
app.get('/', (req, res) => {
  res.json({ status: 'healthy' });
});

app.listen(PORT, () => {
  console.log(`Server listening on port ${PORT}`);
});