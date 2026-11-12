// The base of the wrapper of a tool on Node.js.

// Yields the lines of the stream.
// `node:readline` is not used, because it also splits a line at U+2028 and U+2029, which JSON does not escape.
export async function* lines(stream) {
  stream.setEncoding("utf8");
  let rest = "";
  for await (const chunk of stream) {
    const parts = (rest + chunk).split("\n");
    rest = parts.pop();
    yield* parts;
  }
}

// Reads a request `{"source": ..., "flags": ...}` on each line of the standard input, and writes the result of `check`
// on a line of the standard output.
// `check` also takes the request, which has `attack` when it is to an engine.
export async function serve(check) {
  process.stdout.write(JSON.stringify({ ready: true }) + "\n");
  for await (const line of lines(process.stdin)) {
    const request = JSON.parse(line);
    let result;
    try {
      result = await check(request.source, request.flags, request);
    } catch (error) {
      result = { status: "error", message: String(error) };
    }
    process.stdout.write(JSON.stringify(result) + "\n");
  }
}
