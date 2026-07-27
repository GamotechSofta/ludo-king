import { Token } from "..";
import React from "react";
import type {
  IDiceList,
  IListTokens,
  ISelectTokenValues,
} from "../../../../interfaces";

interface TokensProps {
  diceList: IDiceList[];
  listTokens: IListTokens[];
  isDisabledUI?: boolean;
  debug?: boolean;
  handleSelectedToken: (selectTokenValues: ISelectTokenValues) => void;
}

/**
 * Renders all pawns. Keys are color+index so React never cross-wires
 * player-1 / player-2 tokens when the list is rebuilt.
 */
const Tokens = ({
  listTokens,
  diceList,
  isDisabledUI = false,
  debug = false,
  handleSelectedToken,
}: TokensProps) => (
  <React.Fragment>
    {listTokens.map((group) =>
      group.tokens.map((token) => (
        <Token
          {...token}
          diceList={diceList}
          debug={debug}
          isDisabledUI={isDisabledUI}
          key={`${token.color}-${token.index}`}
          handleSelectedToken={handleSelectedToken}
        />
      ))
    )}
  </React.Fragment>
);

export default React.memo(Tokens);
