/** OHIF v3 · 指向宿主机 Orthanc DICOMweb（浏览器可达 localhost，勿用 Docker 服务名） */
window.config = {
  routerBasename: '/',
  showStudyList: true,
  defaultDataSourceName: 'dicomweb',
  dataSources: [
    {
      namespace: '@ohif/extension-default.dataSourcesModule.dicomweb',
      sourceName: 'dicomweb',
      configuration: {
        friendlyName: 'Whale Orthanc',
        name: 'orthanc',
        wadoUriRoot: 'http://localhost:8042/wado',
        qidoRoot: 'http://localhost:8042/dicom-web',
        wadoRoot: 'http://localhost:8042/dicom-web',
        qidoSupportsIncludeField: false,
        supportsReject: false,
        imageRendering: 'wadors',
        thumbnailRendering: 'wadors',
        enableStudyLazyLoad: true,
        supportsFuzzyMatching: false,
        supportsWildcard: true,
        omitQuotationForMultipartRequest: true,
        dicomUploadEnabled: false,
        bulkDataURI: {
          enabled: true,
        },
      },
    },
  ],
};
