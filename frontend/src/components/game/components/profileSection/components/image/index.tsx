import "./styles.css";
import { TIME_INTERVAL_CHRONOMETER } from "../../../../../../utils/constants";
import { useInterval } from "../../../../../../hooks";
import Avatar from "../../../../../avatar";
import Icon from "../../../../../icon/indext";
import React, { useEffect, useState } from "react";
import type {
  IPlayer,
  TPositionProfile,
  ThandleMuteChat,
} from "../../../../../../interfaces";

interface ImageProps {
  player: IPlayer;
  startTimer: boolean;
  position: TPositionProfile;
  handleMuteChat: ThandleMuteChat;
  handleInterval: (ends: boolean) => void;
}

const PersonSilhouette = () => (
  <div className="game-profile-image-silhouette" aria-hidden>
    <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
      <circle cx="32" cy="22" r="12" fill="#ffffff" />
      <path
        d="M10 58c0-14.4 9.9-24 22-24s22 9.6 22 24"
        fill="#ffffff"
      />
    </svg>
  </div>
);

const Image = ({
  player,
  startTimer,
  position,
  handleMuteChat,
  handleInterval,
}: ImageProps) => {
  const {
    index = 0,
    photo = "",
    isOnline = false,
    isMuted = false,
    isOffline,
  } = player;
  const [progress, setProgress] = useState(1);
  const [isRunning, setIsRunning] = useState(false);

  useEffect(() => {
    setIsRunning(startTimer);
    setProgress(1);
  }, [startTimer]);

  useInterval(
    () => {
      const newProgress = progress + 1;
      setProgress(newProgress);

      if (newProgress === 15) {
        handleInterval(false);
      }

      if (newProgress === 100) {
        setIsRunning(false);
        handleInterval(true);
      }
    },
    isRunning ? TIME_INTERVAL_CHRONOMETER : null
  );

  const style = {
    "--progress": `${Math.round(360 * (progress / 100))}deg`,
  } as React.CSSProperties;

  const titleMuteChat = isMuted ? "Enable chat messages" : "Mute chat messages";

  const styleChatIcon = `game-profile-mute-chat ${position.toLowerCase()} ${
    isMuted ? "mute" : ""
  }`;

  const showMuteChat = isOnline && index !== 0 && !isOffline;
  const useSilhouette = !photo;

  return (
    <div className="game-profile-image">
      {isOffline && <div className="game-profile-image-ofline">Left</div>}
      {useSilhouette ? (
        <PersonSilhouette />
      ) : (
        <Avatar photo={photo} className="game-profile-image-avatar" />
      )}
      {showMuteChat && (
        <button
          title={titleMuteChat}
          className={styleChatIcon}
          onClick={() => handleMuteChat(index)}
        >
          <Icon type="chat" />
        </button>
      )}
      {startTimer && isRunning && (
        <div className="game-profile-image-progress" style={style} />
      )}
    </div>
  );
};

export default React.memo(Image);
