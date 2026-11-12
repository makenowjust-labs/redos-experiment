// Takes `{"source": ..., "flags": ..., "input": ...}` from the standard input, and writes the time of a match of the
// pattern against the input in milliseconds.
let text = "";
process.stdin.setEncoding("utf8");
for await (const chunk of process.stdin) text += chunk;
const { source, flags, input } = JSON.parse(text);

const regex = new RegExp(source, flags);
const start = performance.now();
regex.exec(input);
process.stdout.write(String(performance.now() - start));
