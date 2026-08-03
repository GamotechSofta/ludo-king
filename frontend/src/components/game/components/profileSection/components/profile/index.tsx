import "./styles.css";
import { Image, NameAndDice, Ranking, RenderDice } from "..";
import React, { useCallback } from "react";
import type {
  IActionsTurn,
  IPlayer,
  TPositionProfile,
  TPositionProfiles,
  ThandleDoneDice,
  ThandleMuteChat,
  ThandleSelectDice,
  ThandleTimer,
} from "../../../../../../interfaces";
import {
  MAX_PLAYER_CHANCES,
  TYPES_CHAT_MESSAGES,
} from "../../../../../../utils/constants";

interface ProfileProps {
  basePosition: TPositionProfiles;
  hasTurn: boolean;
  player: IPlayer;
  position: TPositionProfile;
  actionsTurn: IActionsTurn;
  handleTimer: ThandleTimer;
  handleDoneDice: ThandleDoneDice;
  handleSelectDice: ThandleSelectDice;
  handleMuteChat: ThandleMuteChat;
}

const Profile = ({
  basePosition,
  hasTurn,
  player,
  position,
  actionsTurn,
  handleTimer,
  handleDoneDice,
  handleSelectDice,
  handleMuteChat,
}: ProfileProps) => {
  const isEliminated =
    !!player.isEliminated ||
    (player.timeoutStreak ?? 0) >= MAX_PLAYER_CHANCES;
  const lostChances = isEliminated
    ? MAX_PLAYER_CHANCES
    : Math.min(
        MAX_PLAYER_CHANCES,
        Math.max(0, player.timeoutStreak ?? 0)
      );
  const className = `game-profile ${basePosition.toLowerCase()} ${position.toLowerCase()}${
    hasTurn ? " has-turn" : ""
  }${isEliminated ? " is-eliminated" : ""}`;

  const onSelectDice = useCallback(() => {
    if (hasTurn) handleSelectDice();
  }, [hasTurn, handleSelectDice]);

  const chatText = (player.chatMessage || "").trim();
  const showChatBubble = !!chatText && !player.isMuted;
  const chatIsEmoji = player.typeMessage === TYPES_CHAT_MESSAGES.EMOJI;

  return (
    <div className={className}>
      {showChatBubble && (
        <div
          key={player.counterMessage}
          className={`game-profile-chat-bubble${
            chatIsEmoji ? " is-emoji" : ""
          }`}
          role="status"
        >
          {chatText}
        </div>
      )}
      <div className="game-profile-dice-name">
        <Image
          player={player}
          startTimer={hasTurn && actionsTurn.timerActivated && !isEliminated}
          position={position}
          handleMuteChat={handleMuteChat}
          handleInterval={(ends) => handleTimer(ends, player.index)}
          secondsRemaining={
            hasTurn ? actionsTurn.turnSecondsRemaining : undefined
          }
          timeoutSeconds={actionsTurn.turnTimeoutSeconds}
        />
        <div className="game-profile-meta">
          <NameAndDice
            name={player.name}
            diceAvailable={[]}
            hasTurn={hasTurn && !isEliminated}
          />
          <div
            className="game-profile-token-dots"
            aria-label={
              isEliminated
                ? "Lost — no chances left"
                : `${MAX_PLAYER_CHANCES - lostChances} of ${MAX_PLAYER_CHANCES} chances left`
            }
          >
            {Array.from({ length: MAX_PLAYER_CHANCES }, (_, i) => (
              <span
                key={i}
                className={i < lostChances ? "chance-red" : "chance-green"}
              />
            ))}
          </div>
        </div>
        {!isEliminated && (
          <RenderDice
            hasTurn={hasTurn}
            disabledDice={
              !hasTurn || actionsTurn.disabledDice || !actionsTurn.showDice
            }
            showDice
            showArrow={
              hasTurn && !actionsTurn.disabledDice && actionsTurn.showDice
            }
            diceRollNumber={actionsTurn.diceRollNumber}
            value={actionsTurn.diceValue}
            handleDoneDice={handleDoneDice}
            handleSelectDice={onSelectDice}
          />
        )}
      </div>
      {isEliminated ? (
        <div className="game-profile-lost">LOST</div>
      ) : (
        player.finished && player.ranking === 1 && <Ranking value={1} />
      )}
    </div>
  );
};

export default React.memo(Profile);
