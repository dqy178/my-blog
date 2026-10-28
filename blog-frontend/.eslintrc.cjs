module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es2022: true,
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module',
    extraFileExtensions: ['.vue'],
  },
  plugins: ['@typescript-eslint'],
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    'plugin:@typescript-eslint/recommended',
    'eslint-config-prettier',
  ],
  rules: {
    // 单文件组件允许单词命名
    'vue/multi-word-component-names': 'off',
    // 项目里大量使用 any，暂不作为错误
    '@typescript-eslint/no-explicit-any': 'off',
    '@typescript-eslint/no-empty-function': 'off',
    // 未使用变量降级为警告，且允许 _ 前缀
    '@typescript-eslint/no-unused-vars': [
      'warn',
      {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        // catch 里的错误参数常被有意忽略（catch (e) {}）
        caughtErrorsIgnorePattern: '^(e|_)',
      },
    ],
    'no-unused-vars': 'off',
    // Vue 模板中的未使用变量由 vue 插件负责
    'vue/no-unused-vars': 'warn',
    // 项目内 v-html 的内容均经过 DOMPurify.sanitize 消毒，保留该规则作为安全提醒
  },
  ignorePatterns: [
    'dist',
    'node_modules',
    'public',
    '*.timestamp-*.mjs',
  ],
}
