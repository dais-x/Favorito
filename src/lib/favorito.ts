import { registerPlugin } from '@capacitor/core';

export type PromptType = 'voice' | 'subtle';
export type LikeActionMode = 'intent' | 'queue';

export type FavoritoSettings = {
  masterEnabled: boolean;
  promptType: PromptType;
  minimumDurationSeconds: number;
  volumeSkipEnabled: boolean;
  likeActionMode: LikeActionMode;
  youtubeOauthTokenConfigured: boolean;
};

type FavoritoPlugin = {
  getSettings(): Promise<FavoritoSettings>;
  setMasterEnabled(options: { enabled: boolean }): Promise<FavoritoSettings>;
  setPromptType(options: { promptType: PromptType }): Promise<FavoritoSettings>;
  setMinimumDurationSeconds(options: { seconds: number }): Promise<FavoritoSettings>;
  setVolumeSkipEnabled(options: { enabled: boolean }): Promise<FavoritoSettings>;
  setLikeActionMode(options: { mode: LikeActionMode }): Promise<FavoritoSettings>;
  setYoutubeOauthToken(options: { token: string }): Promise<FavoritoSettings>;
  openNotificationListenerSettings(): Promise<void>;
  openAccessibilitySettings(): Promise<void>;
};

export const Favorito = registerPlugin<FavoritoPlugin>('Favorito');
