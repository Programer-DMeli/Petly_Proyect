type Step = { number: number; text: string };
type Block = { lines: string[] } | { steps: Step[] };

export default function AsistenteRespuesta({ text }: { text: string }) {
  const blocks: Block[] = [];
  for (const line of text.split(/\r?\n/)) {
    const previous = blocks.at(-1);
    const step = /^\s*([1-9]\d{0,2})[.)]\s+(.+)$/.exec(line);
    if (step) {
      const item = { number: Number(step[1]), text: step[2].trim() };
      if (previous && 'steps' in previous) previous.steps.push(item);
      else blocks.push({ steps: [item] });
    } else if (previous && 'lines' in previous) {
      previous.lines.push(line);
    } else if (line.trim()) {
      blocks.push({ lines: [line] });
    }
  }

  return (
    <div className="petly-assistant-reply">
      {blocks.map((block, index) =>
        'steps' in block ? (
          <ol key={index} start={block.steps[0].number}>
            {block.steps.map((step, stepIndex) => (
              <li key={stepIndex} value={step.number}>
                {step.text}
              </li>
            ))}
          </ol>
        ) : (
          block.lines
            .join('\n')
            .trim()
            .split(/\n(?:[ \t]*\n)+/)
            .map((paragraph, paragraphIndex) => (
              <p key={`${index}-${paragraphIndex}`}>{paragraph}</p>
            ))
        )
      )}
    </div>
  );
}
