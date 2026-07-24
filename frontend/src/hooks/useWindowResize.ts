import { useEffect } from "react";
import resizeScreen, { applyScreenResize } from "../utils/resize-screen";

const useWindowResize = () => {
  useEffect(() => {
    // Immediate fit on mount / refresh (no debounce delay)
    applyScreenResize();
    requestAnimationFrame(applyScreenResize);

    window.addEventListener("resize", resizeScreen);
    window.addEventListener("orientationchange", resizeScreen);
    window.visualViewport?.addEventListener("resize", resizeScreen);
    window.visualViewport?.addEventListener("scroll", resizeScreen);

    return () => {
      window.removeEventListener("resize", resizeScreen);
      window.removeEventListener("orientationchange", resizeScreen);
      window.visualViewport?.removeEventListener("resize", resizeScreen);
      window.visualViewport?.removeEventListener("scroll", resizeScreen);
    };
  }, []);
};

export default useWindowResize;
