// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';
/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'SonarValidator 문서', tagline: '구조 · 정책 · 배포 · 운영',
  favicon: 'img/favicon.ico', url: 'http://localhost:3001', baseUrl: '/',
  organizationName: 'wayfarer6', projectName: 'SonarValidator',
  onBrokenLinks: 'throw', i18n: {defaultLocale: 'ko', locales: ['ko']},
  themes: ['@docusaurus/theme-mermaid'], markdown: {mermaid: true},
  presets: [['classic', {
    docs: {sidebarPath: './sidebars.js', editUrl: 'https://github.com/wayfarer6/SonarValidator/edit/main/docs/'},
    blog: false, theme: {customCss: './src/css/custom.css'},
  }]],
  themeConfig: {
    colorMode: {respectPrefersColorScheme: true},
    navbar: {title: 'SonarValidator', items: [
      {type: 'docSidebar', sidebarId: 'docsSidebar', label: '기능별 문서', position: 'left'},
      {to: '/docs/deployment/lab1', label: 'Lab 1 배포', position: 'left'},
      {href: 'https://github.com/wayfarer6/SonarValidator', label: 'GitHub', position: 'right'},
    ]},
    footer: {style: 'dark', links: [{title: 'SonarValidator', items: [
      {label: '문서 안내', to: '/docs/intro'}, {label: '운영', to: '/docs/operations/runbook'},
    ]}], copyright: 'SonarValidator'},
    prism: {theme: prismThemes.github, darkTheme: prismThemes.dracula},
  },
};
export default config;
