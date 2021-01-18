const fs = require('fs');

const find = (filename, team = "B") => {
  const content = `${fs.readFileSync(filename)}`.split("\n");
  let i = 0;
  for (const line of content) {
    if (line[1] == team) {
      if (line.search("Exception") != -1) {
        console.log(line);

      if (line + 10 < content.length) {
        console.log(content.slice(i, i+10))
      }
      }
    }
    i++;
  }
}
find("output");