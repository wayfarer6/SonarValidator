const express = require('express');
const app = express();
const port = 3000;

// Define a GET API route for the root path
app.get('/', (req, res) => {
  res.send('Hello World!');
});


app.get('./api/heartbeat', (req,res) => {
    res.senbd("Hello");
});
// Start the server and listen on the defined port
app.listen(port, () => {
  console.log(`Application listening at http://localhost:${port}`);
});
