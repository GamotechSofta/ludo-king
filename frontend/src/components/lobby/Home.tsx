import React from "react";
import "./styles.css";

interface HomeProps {
  onPlay: () => void;
}

const Home = ({ onPlay }: HomeProps) => {
  return (
    <div className="lobby">
      <div className="lobby-top">
        <div className="lobby-brand">
          <div className="lobby-crown" aria-hidden />
          <h1 className="lobby-title">
            LUDO
            <span>KING</span>
          </h1>
          <p className="lobby-tagline">Roll · Race · Rule the board</p>
        </div>
        <div className="lobby-tokens" aria-hidden>
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
        </div>
      </div>

      <div className="lobby-actions">
        <button className="lobby-btn primary" type="button" onClick={onPlay}>
          PLAY NOW
        </button>
        <p className="lobby-footer-note">Online matchmaking · smooth Ludo</p>
      </div>
    </div>
  );
};

export default React.memo(Home);
