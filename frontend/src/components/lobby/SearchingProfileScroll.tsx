import React from "react";

const SCROLL_PROFILES = [
  { bg: "linear-gradient(180deg, #7eb6ff 0%, #3a6fc4 100%)", skin: "#f5c8a8" },
  { bg: "linear-gradient(180deg, #ff9a9e 0%, #c94b4b 100%)", skin: "#e8b090" },
  { bg: "linear-gradient(180deg, #a8e063 0%, #56ab2f 100%)", skin: "#c68642" },
  { bg: "linear-gradient(180deg, #ffd194 0%, #d1913c 100%)", skin: "#f5d0a8" },
];

const ProfileSilhouette = ({ skin }: { skin: string }) => (
  <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg" aria-hidden>
    <circle cx="32" cy="22" r="11" fill={skin} />
    <path
      d="M10 58c0-13.5 9.5-22 22-22s22 8.5 22 22"
      fill={skin}
    />
  </svg>
);

const SearchingProfileScroll = ({ delayMs = 0 }: { delayMs?: number }) => {
  const items = [...SCROLL_PROFILES, ...SCROLL_PROFILES];

  return (
    <div className="find-players-search-scroll">
      <div
        className="find-players-search-scroll-track"
        style={{ animationDelay: `${delayMs}ms` }}
      >
        {items.map((profile, i) => (
          <div
            key={i}
            className="find-players-search-scroll-item"
            style={{ background: profile.bg }}
          >
            <ProfileSilhouette skin={profile.skin} />
          </div>
        ))}
      </div>
    </div>
  );
};

export default React.memo(SearchingProfileScroll);
