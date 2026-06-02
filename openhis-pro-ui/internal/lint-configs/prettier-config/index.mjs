export default {
  endOfLine: 'lf',
  overrides: [
    {
      files: ['*.json5'],
      options: {
        quoteProps: 'preserve',
        singleQuote: false,
      },
    },
  ],
  plugins: ['prettier-plugin-tailwindcss'],
  printWidth: 100,
  proseWrap: 'never',
  semi: true,
  singleQuote: true,
  trailingComma: 'all',
};
