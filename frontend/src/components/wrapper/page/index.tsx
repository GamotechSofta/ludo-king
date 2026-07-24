import "./styles.css";
import "../../lobby/styles.css";
import React, { ReactNode } from "react";

interface PageWrapperProps {
  children: JSX.Element | JSX.Element[] | ReactNode;
  leftOption?: JSX.Element;
  rightOption?: JSX.Element;
}

const PageWrapper = ({
  children,
  leftOption,
  rightOption,
}: PageWrapperProps) => (
  <div className="page-wrapper">
    {(leftOption || rightOption) && (
      <div className="page-wrapper-options">
        <div>{leftOption}</div>
        <div>{rightOption}</div>
      </div>
    )}
    {children}
  </div>
);

export default React.memo(PageWrapper);
