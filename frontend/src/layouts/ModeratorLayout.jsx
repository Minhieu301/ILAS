import React from "react";
import { Outlet, useLocation } from "react-router-dom";
import ChatWidget from "../components/chatbot/ChatWidget";
import "../styles/moderator/ModeratorLayout.css";

export default function ModeratorLayout() {
  const location = useLocation();

  const isModeratorWorkspace =
    location.pathname.startsWith("/moderator/") ||
    location.pathname.startsWith("/moderator/");

  // =========================
  // HIDE CHAT WIDGET ON SOME ROUTES
  // =========================
  const hideWidgetPaths = ["/chat/history"];
  const showWidget = !hideWidgetPaths.some((path) =>
    location.pathname.startsWith(path)
  );

  if (isModeratorWorkspace) {
    return (
      <main style={{ minHeight: "75vh" }}>
        <Outlet />
      </main>
    );
  }

  return (
    <div className="moderator-layout">
      <main className="moderator-main">
        <div className="moderator-content-wrapper">
          <Outlet />
        </div>
      </main>

      {/* MINI CHAT WIDGET */}
      {showWidget && <ChatWidget />}
    </div>
  );
}

