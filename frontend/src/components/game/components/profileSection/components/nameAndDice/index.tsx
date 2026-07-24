import "./styles.css";
import Dice from "../../../dice";
import React from "react";
import type { IDiceList } from "../../../../../../interfaces";

interface NameAndDiceProps {
  name: string;
  diceAvailable: IDiceList[];
  hasTurn: boolean;
}

const NameAndDice = ({
  name,
  diceAvailable = [],
  hasTurn = false,
}: NameAndDiceProps) => (
  <div className="game-profile-name-dice">
    <div className={`game-profile-name ${hasTurn ? "has-turn" : ""}`}>
      {name}
    </div>
    {diceAvailable.length !== 0 && (
      <div className="game-profile-dices">
        {diceAvailable.map(({ key, value }) => (
          <Dice key={key} value={value} size={14} animate />
        ))}
      </div>
    )}
  </div>
);

export default React.memo(NameAndDice);
