type ReportHandler = (metric: {
  name: string;
  value: number;
  id: string;
}) => void;

type WebVitalsModule = {
  onCLS: (onReport: ReportHandler) => void;
  onINP: (onReport: ReportHandler) => void;
  onFCP: (onReport: ReportHandler) => void;
  onLCP: (onReport: ReportHandler) => void;
  onTTFB: (onReport: ReportHandler) => void;
};

const reportWebVitals = (onPerfEntry?: ReportHandler) => {
  if (onPerfEntry && onPerfEntry instanceof Function) {
    // Cast needed: CRA (TS 4.9 + moduleResolution "node") does not resolve
    // web-vitals v6 ESM typings correctly for dynamic imports.
    import("web-vitals").then((mod) => {
      const { onCLS, onINP, onFCP, onLCP, onTTFB } = mod as unknown as WebVitalsModule;
      onCLS(onPerfEntry);
      onINP(onPerfEntry);
      onFCP(onPerfEntry);
      onLCP(onPerfEntry);
      onTTFB(onPerfEntry);
    });
  }
};

export default reportWebVitals;
