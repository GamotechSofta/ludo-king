import "./styles.css";
import { Image, NameAndDice, Ranking, RenderDice } from "..";
import React from "react";
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
  const className = `game-profile ${basePosition.toLowerCase()} ${position.toLowerCase()}${
    hasTurn ? " has-turn" : ""
  }`;
  const colorClass = (player.color || "RED").toLowerCase();

  return (
    <div className={className}>
      <div className="game-profile-dice-name">
        <Image
          player={player}
          startTimer={hasTurn && actionsTurn.timerActivated}
          position={position}
          handleMuteChat={handleMuteChat}
          handleInterval={(ends) => handleTimer(ends, player.index)}
        />
        <div className="game-profile-meta">
          <NameAndDice
            name={player.name}
            diceAvailable={[]}
            hasTurn={hasTurn}
          />
          <div className="game-profile-token-dots" aria-hidden>
            {[0, 1, 2, 3].map((i) => (
              <span key={i} className={`on ${colorClass}`} />
            ))}
          </div>
        </div>
        <RenderDice
          hasTurn={hasTurn}
          disabledDice={
            !hasTurn || actionsTurn.disabledDice || !actionsTurn.showDice
          }
          showDice
          showArrow={hasTurn && !actionsTurn.disabledDice && actionsTurn.showDice}
          diceRollNumber={hasTurn ? actionsTurn.diceRollNumber : 0}
          value={hasTurn ? actionsTurn.diceValue : 0}
          handleDoneDice={handleDoneDice}
          handleSelectDice={() => {
            if (hasTurn) handleSelectDice();
          }}
        />
      </div>
      {player.finished && <Ranking value={player.ranking} />}
    </div>
  );
};

export default React.memo(Profile);
