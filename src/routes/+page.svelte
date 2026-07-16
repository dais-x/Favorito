<script lang="ts">
  import { onMount } from 'svelte';
  import { Favorito, type FavoritoSettings, type LikeActionMode, type PromptType } from '$lib/favorito';

  let settings: FavoritoSettings = {
    masterEnabled: false,
    promptType: 'voice',
    minimumDurationSeconds: 90,
    volumeSkipEnabled: false,
    likeActionMode: 'intent',
    youtubeOauthTokenConfigured: false
  };
  let token = '';
  let saving = false;

  onMount(async () => {
    settings = await Favorito.getSettings();
  });

  async function save<T>(operation: () => Promise<T>) {
    saving = true;
    try {
      const result = await operation();
      if (result && typeof result === 'object') settings = result as FavoritoSettings;
    } finally {
      saving = false;
    }
  }

  const setPromptType = (promptType: PromptType) =>
    save(() => Favorito.setPromptType({ promptType }));

  const setLikeActionMode = (mode: LikeActionMode) =>
    save(() => Favorito.setLikeActionMode({ mode }));
</script>

<svelte:head>
  <title>Favorito</title>
</svelte:head>

<main class="shell">
  <section class="header">
    <div>
      <p class="eyebrow">YouTube Music companion</p>
      <h1>Favorito</h1>
    </div>
    <label class="switch">
      <input
        type="checkbox"
        checked={settings.masterEnabled}
        disabled={saving}
        on:change={(event) =>
          save(() => Favorito.setMasterEnabled({ enabled: event.currentTarget.checked }))} />
      <span>Master</span>
    </label>
  </section>

  <section class="panel">
    <div class="row">
      <div>
        <h2>Prompt</h2>
        <p>Choose how Favorito asks before opening the microphone.</p>
      </div>
      <div class="segmented" aria-label="Prompt type">
        <button
          class:active={settings.promptType === 'voice'}
          on:click={() => setPromptType('voice')}
          type="button">Voice</button>
        <button
          class:active={settings.promptType === 'subtle'}
          on:click={() => setPromptType('subtle')}
          type="button">Subtle</button>
      </div>
    </div>

    <label class="field">
      <span>Minimum song length</span>
      <input
        type="number"
        min="1"
        inputmode="numeric"
        value={settings.minimumDurationSeconds}
        on:change={(event) =>
          save(() => Favorito.setMinimumDurationSeconds({ seconds: Number(event.currentTarget.value) }))} />
      <small>seconds</small>
    </label>
  </section>

  <section class="panel">
    <div class="row">
      <div>
        <h2>Volume keys</h2>
        <p>Long-press volume up/down while music is playing to skip tracks.</p>
      </div>
      <label class="switch compact">
        <input
          type="checkbox"
          checked={settings.volumeSkipEnabled}
          disabled={saving}
          on:change={(event) =>
            save(() => Favorito.setVolumeSkipEnabled({ enabled: event.currentTarget.checked }))} />
        <span>Skip</span>
      </label>
    </div>
    <button class="secondary" type="button" on:click={() => Favorito.openAccessibilitySettings()}>
      Open Accessibility Settings
    </button>
  </section>

  <section class="panel">
    <div class="row">
      <div>
        <h2>Like action</h2>
        <p>Switch between the undocumented widget broadcast and the offline YouTube API queue.</p>
      </div>
      <div class="segmented" aria-label="Like action mode">
        <button
          class:active={settings.likeActionMode === 'intent'}
          on:click={() => setLikeActionMode('intent')}
          type="button">Intent</button>
        <button
          class:active={settings.likeActionMode === 'queue'}
          on:click={() => setLikeActionMode('queue')}
          type="button">Queue</button>
      </div>
    </div>

    <label class="field">
      <span>YouTube OAuth token</span>
      <input
        type="password"
        placeholder={settings.youtubeOauthTokenConfigured ? 'Configured' : 'Paste access token'}
        bind:value={token} />
      <button
        class="secondary"
        type="button"
        disabled={!token.trim()}
        on:click={() => save(() => Favorito.setYoutubeOauthToken({ token }))}>
        Save
      </button>
    </label>
  </section>

  <section class="panel">
    <h2>System access</h2>
    <div class="actions">
      <button type="button" on:click={() => Favorito.openNotificationListenerSettings()}>
        Notification Access
      </button>
      <button type="button" on:click={() => Favorito.openAccessibilitySettings()}>
        Accessibility
      </button>
    </div>
  </section>
</main>

<style>
  :global(body) {
    margin: 0;
    background: #f7f3ea;
    color: #18202a;
    font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  }

  .shell {
    width: min(760px, calc(100vw - 32px));
    margin: 0 auto;
    padding: 28px 0;
  }

  .header,
  .panel {
    border: 1px solid #d9d2c3;
    background: #fffdf8;
    border-radius: 8px;
  }

  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 22px;
    margin-bottom: 14px;
  }

  .panel {
    padding: 18px;
    margin: 14px 0;
  }

  .eyebrow {
    margin: 0 0 4px;
    color: #5e6978;
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0;
  }

  h1,
  h2,
  p {
    margin: 0;
  }

  h1 {
    font-size: 2rem;
    line-height: 1.1;
  }

  h2 {
    font-size: 1rem;
    margin-bottom: 4px;
  }

  p,
  small {
    color: #5e6978;
  }

  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
  }

  .switch {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    min-width: 112px;
    justify-content: flex-end;
    font-weight: 700;
  }

  .switch.compact {
    min-width: 88px;
  }

  .switch input {
    width: 48px;
    height: 28px;
    accent-color: #1f6feb;
  }

  .segmented {
    display: grid;
    grid-template-columns: repeat(2, minmax(76px, 1fr));
    border: 1px solid #c8d1dc;
    border-radius: 8px;
    overflow: hidden;
  }

  button {
    min-height: 42px;
    border: 0;
    background: #1f6feb;
    color: white;
    font: inherit;
    font-weight: 700;
    padding: 0 14px;
  }

  .segmented button {
    background: transparent;
    color: #263241;
  }

  .segmented button.active {
    background: #1f6feb;
    color: white;
  }

  .secondary {
    margin-top: 14px;
    border: 1px solid #c8d1dc;
    background: #eef4fb;
    color: #1a4b88;
  }

  .field {
    display: grid;
    grid-template-columns: 1fr minmax(96px, 180px) auto;
    align-items: center;
    gap: 10px;
    margin-top: 18px;
  }

  input[type="number"],
  input[type="password"] {
    min-height: 42px;
    border: 1px solid #c8d1dc;
    border-radius: 8px;
    padding: 0 12px;
    background: white;
    color: #18202a;
    font: inherit;
  }

  .actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
    margin-top: 14px;
  }

  @media (max-width: 620px) {
    .header,
    .row,
    .field {
      grid-template-columns: 1fr;
      align-items: stretch;
    }

    .row,
    .header {
      flex-direction: column;
      align-items: stretch;
    }

    .switch {
      justify-content: space-between;
    }

    .actions {
      grid-template-columns: 1fr;
    }
  }
</style>
