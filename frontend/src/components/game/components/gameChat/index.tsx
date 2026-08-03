import "./styles.css";
import React, { useCallback, useState } from "react";
import Icon from "../../../icon/indext";
import type { ITypeChatMessage } from "../../../../interfaces";
import {
  PREDEFINED_CHAT_MESSAGES,
  TYPES_CHAT_MESSAGES,
} from "../../../../utils/constants";

export interface GameChatSendPayload {
  type: ITypeChatMessage;
  messageIndex: number;
  message: string;
}

interface GameChatProps {
  onSend: (payload: GameChatSendPayload) => void;
  disabled?: boolean;
}

const GameChat = ({ onSend, disabled = false }: GameChatProps) => {
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState<ITypeChatMessage>(TYPES_CHAT_MESSAGES.TEXT);

  const close = useCallback(() => setOpen(false), []);

  const handlePick = useCallback(
    (type: ITypeChatMessage, messageIndex: number, message: string) => {
      if (disabled) return;
      onSend({ type, messageIndex, message });
      setOpen(false);
    },
    [disabled, onSend]
  );

  const messages = PREDEFINED_CHAT_MESSAGES[tab] ?? [];

  return (
    <>
      {open && (
        <button
          type="button"
          className="game-chat-backdrop"
          aria-label="Close chat"
          onClick={close}
        />
      )}
      <div className="game-chat">
        {open && (
          <div className="game-chat-panel" role="dialog" aria-label="Quick chat">
            <div className="game-chat-tabs">
              <button
                type="button"
                className={`game-chat-tab${
                  tab === TYPES_CHAT_MESSAGES.TEXT ? " is-active" : ""
                }`}
                onClick={() => setTab(TYPES_CHAT_MESSAGES.TEXT)}
              >
                Text
              </button>
              <button
                type="button"
                className={`game-chat-tab${
                  tab === TYPES_CHAT_MESSAGES.EMOJI ? " is-active" : ""
                }`}
                onClick={() => setTab(TYPES_CHAT_MESSAGES.EMOJI)}
              >
                Emoji
              </button>
            </div>
            <div
              className={`game-chat-list${
                tab === TYPES_CHAT_MESSAGES.EMOJI ? " is-emoji" : ""
              }`}
            >
              {messages.map((item) => (
                <button
                  key={`${tab}-${item.index}`}
                  type="button"
                  className="game-chat-item"
                  onClick={() => handlePick(tab, item.index, item.value)}
                >
                  {item.value}
                </button>
              ))}
            </div>
          </div>
        )}
        <button
          type="button"
          className={`game-chat-btn${open ? " is-open" : ""}`}
          aria-label={open ? "Close chat" : "Open chat"}
          aria-expanded={open}
          disabled={disabled}
          onClick={() => setOpen((v) => !v)}
        >
          <Icon type="chat" />
        </button>
      </div>
    </>
  );
};

export default React.memo(GameChat);
