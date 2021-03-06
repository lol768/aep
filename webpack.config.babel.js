import * as builder from '@universityofwarwick/webpack-config-builder';

export default builder.playApp()
  .jsEntries({
    admin: './app/assets/js/admin.js',
    render: './app/assets/js/render.js',
  })
  .cssEntries({
    style: './app/assets/css/main.less',
    'admin-style': './app/assets/css/admin.less',
  })
  .momentTimezonesFrom(2020)
  .addBrowserLevel({ id: 'modern', suffix: '-modern', babelTargets: {
    chrome: '75',
    edge: '44',
    firefox: '70',
    safari: '11.0',
    samsung: '8.0',
  }})
  .copyModule('@universityofwarwick/id7', 'dist', 'lib/id7')
  .copyModule('@fortawesome/fontawesome-pro', 'webfonts', 'lib/fontawesome-pro/webfonts')
  .build();
