package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Arrays;
import java.util.ArrayList;
import java.io.File;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    private static final int SCREEN_WORKSPACE = 0;
    private static final int SCREEN_TERMINAL = 1;
    private static final int SCREEN_FILES = 2;
    private static final int SCREEN_TOOLS = 3;
    private static final int SCREEN_SETTINGS = 4;
    private static final String DEVBOX_UI_PREFERENCES = "devbox_ui_preferences";
    private static final String KEY_TERMINAL_CANVAS_INSET = "terminal_canvas_inset_dp";
    private static final String KEY_TERMINAL_CANVAS_PRESET = "terminal_canvas_preset";
    private int mSelectedScreen = SCREEN_WORKSPACE;
    private int mTerminalCanvasInsetDp;
    private int mTerminalCanvasPreset = -1;
    private File mCurrentFileDirectory;
    private LinearLayout mWorkspaceContent;
    private LinearLayout mFilesContent;
    private LinearLayout mToolsContent;
    private LinearLayout mSettingsContent;
    private TextView mWorkspaceSessionSummary;
    private TextView mFilesPathLabel;
    private TextView mTerminalFontSizeLabel;
    private SeekBar mTerminalFontSizeSeekBar;
    private LinearLayout mTerminalPresetOptions;
    private LinearLayout mFileList;
    private final ArrayList<View> mNavigationItems = new ArrayList<>();

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    private BottomSheetDialog mSessionsDialog;
    private ListView mTerminalSessionsListView;
    private TextView mSessionCountView;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;
    private float mTerminalToolbarDefaultHeight;


    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";
    private static final String ARG_SELECTED_APP_SCREEN = "selected_app_screen";
    private static final String ARG_FILE_BROWSER_PATH = "file_browser_path";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        android.content.SharedPreferences uiPreferences = getSharedPreferences(DEVBOX_UI_PREFERENCES, MODE_PRIVATE);
        mTerminalCanvasInsetDp = uiPreferences.getInt(KEY_TERMINAL_CANVAS_INSET, 0);
        mTerminalCanvasPreset = uiPreferences.getInt(KEY_TERMINAL_CANVAS_PRESET, -1);

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                android.view.WindowInsets result = super.onApplyWindowInsets(view, insets);
                View navigation = findViewById(R.id.app_navigation);
                if (navigation != null) {
                    boolean keyboardVisible = WindowInsetsCompat.toWindowInsetsCompat(insets)
                        .isVisible(WindowInsetsCompat.Type.ime());
                    navigation.setVisibility(mSelectedScreen == SCREEN_TERMINAL && keyboardVisible ? View.GONE : View.VISIBLE);
                }
                return result;
            }
        });

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setBottomBarView();
        setupPremiumShell();

        Intent launchIntent = getIntent();
        if (savedInstanceState != null) {
            mCurrentFileDirectory = new File(savedInstanceState.getString(ARG_FILE_BROWSER_PATH,
                TermuxConstants.TERMUX_HOME_DIR_PATH));
            selectAppScreen(savedInstanceState.getInt(ARG_SELECTED_APP_SCREEN, SCREEN_WORKSPACE));
        } else if (launchIntent != null && (Intent.ACTION_RUN.equals(launchIntent.getAction()) ||
            launchIntent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false))) {
            selectAppScreen(SCREEN_TERMINAL);
        }

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        closeSessionsPanel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
        savedInstanceState.putInt(ARG_SELECTED_APP_SCREEN, mSelectedScreen);
        savedInstanceState.putString(ARG_FILE_BROWSER_PATH, mCurrentFileDirectory == null ?
            TermuxConstants.TERMUX_HOME_DIR_PATH : mCurrentFileDirectory.getAbsolutePath());
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);
        refreshPremiumShell();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginHorizontal = mProperties.getTerminalMarginHorizontal() + mTerminalCanvasInsetDp;
        int marginVertical = mProperties.getTerminalMarginVertical() + mTerminalCanvasInsetDp;
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        View sheet = getLayoutInflater().inflate(R.layout.sheet_terminal_sessions, null, false);
        mTerminalSessionsListView = sheet.findViewById(R.id.terminal_sessions_list);
        mSessionCountView = sheet.findViewById(R.id.session_sheet_count);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, mTermuxService.getTermuxSessions());
        mTerminalSessionsListView.setAdapter(mTermuxSessionListViewController);
        mTerminalSessionsListView.setOnItemClickListener(mTermuxSessionListViewController);
        mTerminalSessionsListView.setOnItemLongClickListener(mTermuxSessionListViewController);

        mSessionsDialog = new BottomSheetDialog(this);
        mSessionsDialog.setContentView(sheet);
        mSessionsDialog.setOnShowListener(dialog -> {
            View bottomSheet = mSessionsDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
            WindowManager.LayoutParams attributes = mSessionsDialog.getWindow().getAttributes();
            attributes.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attributes.setBlurBehindRadius(28);
            mSessionsDialog.getWindow().setAttributes(attributes);
            mSessionsDialog.getWindow().setDimAmount(0.20f);
        });
        sheet.findViewById(R.id.session_sheet_add).setOnClickListener(v -> {
            closeSessionsPanel();
            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
        });
        updateSessionCount();
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setBottomBarView() {
        LinearLayout bar = findViewById(R.id.terminal_bottom_bar);
        addBottomBarAction(bar, R.drawable.ic_terminal_sessions, R.string.action_sessions, v -> openSessionsPanel(), null);
        addBottomBarAction(bar, R.drawable.ic_terminal_keyboard, R.string.action_toggle_soft_keyboard,
            v -> mTermuxTerminalViewClient.onToggleSoftKeyboardRequest(), v -> {
                toggleTerminalToolbar();
                return true;
            });
        View newSessionAction = addBottomBarAction(bar, R.drawable.ic_terminal_add, R.string.action_new_session,
            v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null), v -> {
                TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                    R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                    R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                    -1, null, null);
                return true;
            });
        newSessionAction.findViewById(R.id.action_icon).setBackgroundResource(R.drawable.bg_glass_icon_button);
        addBottomBarAction(bar, R.drawable.ic_terminal_more, R.string.action_more,
            v -> mTerminalView.showContextMenu(), null);
    }

    private View addBottomBarAction(LinearLayout bar, int icon, int label, View.OnClickListener click,
                                    View.OnLongClickListener longClick) {
        View action = getLayoutInflater().inflate(R.layout.item_terminal_bottom_action, bar, false);
        ImageView image = action.findViewById(R.id.action_icon);
        image.setImageResource(icon);
        TextView text = action.findViewById(R.id.action_label);
        text.setText(label);
        action.setContentDescription(getString(label));
        action.setOnClickListener(click);
        if (longClick != null) action.setOnLongClickListener(longClick);
        bar.addView(action);
        return action;
    }

    private void setupPremiumShell() {
        mWorkspaceContent = createPageContent(findViewById(R.id.workspace_screen));
        mFilesContent = createPageContent(findViewById(R.id.files_screen));
        mToolsContent = createPageContent(findViewById(R.id.tools_screen));
        mSettingsContent = createPageContent(findViewById(R.id.app_settings_screen));
        mCurrentFileDirectory = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);

        buildWorkspacePage();
        buildFilesPage();
        buildToolsPage();
        buildSettingsPage();

        LinearLayout navigation = findViewById(R.id.app_navigation);
        addNavigationItem(navigation, "Home", R.drawable.ic_nav_workspace, SCREEN_WORKSPACE);
        addNavigationItem(navigation, "Terminal", R.drawable.ic_nav_terminal, SCREEN_TERMINAL);
        addNavigationItem(navigation, "Files", R.drawable.ic_nav_files, SCREEN_FILES);
        addNavigationItem(navigation, "Tools", R.drawable.ic_nav_tools, SCREEN_TOOLS);
        addNavigationItem(navigation, "Settings", R.drawable.ic_nav_settings, SCREEN_SETTINGS);
        findViewById(R.id.terminal_session_button).setOnClickListener(v -> openSessionsPanel());
        selectAppScreen(SCREEN_WORKSPACE);
    }

    private LinearLayout createPageContent(FrameLayout host) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        host.addView(scrollView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return content;
    }

    private void addNavigationItem(LinearLayout navigation, String label, int icon, int screen) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setBackgroundResource(R.drawable.bg_nav_item_selected);
        item.setPadding(dp(2), dp(7), dp(2), dp(5));
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(21), dp(21));
        item.addView(image, imageParams);
        TextView title = premiumText(label, 9, true, R.color.glass_text_secondary);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(3);
        item.addView(title, titleParams);
        item.setTag(new View[]{image, title});
        item.setContentDescription(label);
        item.setOnClickListener(v -> selectAppScreen(screen));
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(0, dp(54), 1f);
        itemParams.setMargins(dp(2), 0, dp(2), 0);
        navigation.addView(item, itemParams);
        mNavigationItems.add(item);
    }

    private void selectAppScreen(int screen) {
        int previousScreen = mSelectedScreen;
        mSelectedScreen = screen;
        int[] screenIds = {R.id.workspace_screen, R.id.terminal_screen, R.id.files_screen,
            R.id.tools_screen, R.id.app_settings_screen};
        for (int i = 0; i < screenIds.length; i++) {
            View page = findViewById(screenIds[i]);
            if (page != null) page.setVisibility(i == screen ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < mNavigationItems.size(); i++) {
            View item = mNavigationItems.get(i);
            View[] parts = (View[]) item.getTag();
            boolean selected = i == screen;
            item.setBackgroundResource(selected ? R.drawable.bg_nav_item_selected : android.R.color.transparent);
            ((ImageView) parts[0]).setColorFilter(getColor(selected ? R.color.neon_violet_soft : R.color.glass_text_secondary));
            ((TextView) parts[1]).setTextColor(getColor(selected ? R.color.glass_text_primary : R.color.glass_text_secondary));
        }
        if (screen == SCREEN_FILES) refreshFileList();
        if (screen == SCREEN_WORKSPACE) refreshPremiumShell();
        if (screen == SCREEN_TERMINAL && mTerminalViewClientReady()) mTermuxTerminalViewClient.onTerminalScreenSelected();
        else if (previousScreen == SCREEN_TERMINAL && mTerminalViewClientReady()) mTermuxTerminalViewClient.onTerminalScreenHidden();
    }

    private boolean mTerminalViewClientReady() {
        return mTermuxTerminalViewClient != null && mTerminalView != null;
    }

    public boolean isTerminalScreenSelected() {
        return mSelectedScreen == SCREEN_TERMINAL;
    }

    private void buildWorkspacePage() {
        mWorkspaceContent.removeAllViews();
        addPageHeader(mWorkspaceContent, "DEVBOX  /  LOCAL WORKSPACE", "Your space,\nyour commands.",
            "A focused home for the work happening on your device.");

        LinearLayout hero = premiumCard(true);
        addText(hero, "WORKSPACE STATUS", 10, true, R.color.neon_violet_soft, 0, 2);
        addText(hero, "Ready when you are.", 21, true, R.color.glass_text_primary, 0, 5);
        mWorkspaceSessionSummary = addText(hero, "Starting terminal service…", 13, false,
            R.color.glass_text_secondary, 0, 16);
        addPillButton(hero, "Open terminal", () -> selectAppScreen(SCREEN_TERMINAL));
        mWorkspaceContent.addView(hero, pageLayout());

        addSectionTitle(mWorkspaceContent, "Quick access", "Pick up a common task.");
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout newSession = miniCard("＋", "New session", "Fresh shell", () -> {
            mTermuxTerminalSessionActivityClient.addNewSession(false, null);
            selectAppScreen(SCREEN_TERMINAL);
        });
        LinearLayout files = miniCard("⌘", "Browse files", "Your home folder", () -> selectAppScreen(SCREEN_FILES));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(118), 1f);
        half.setMargins(0, 0, dp(6), 0);
        quickRow.addView(newSession, half);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(0, dp(118), 1f);
        halfRight.setMargins(dp(6), 0, 0, 0);
        quickRow.addView(files, halfRight);
        mWorkspaceContent.addView(quickRow, pageLayout());

        addSectionTitle(mWorkspaceContent, "Recent sessions", "Jump back into an active shell.");
        LinearLayout recent = new LinearLayout(this);
        recent.setOrientation(LinearLayout.VERTICAL);
        recent.setTag("recent_sessions");
        mWorkspaceContent.addView(recent, pageLayout());
        refreshPremiumShell();
    }

    public void refreshPremiumShell() {
        if (mWorkspaceSessionSummary == null || mTermuxService == null) return;
        int count = mTermuxService.getTermuxSessionsSize();
        mWorkspaceSessionSummary.setText(count == 0 ? "No active sessions yet. Start a terminal to begin."
            : count + (count == 1 ? " active session is ready." : " active sessions are ready."));
        TextView terminalTitle = findViewById(R.id.terminal_session_title);
        TextView terminalSessionButton = findViewById(R.id.terminal_session_button);
        TerminalSession current = getCurrentSession();
        String currentTitle = current == null ? "Local shell" : current.mSessionName;
        if (currentTitle == null || currentTitle.trim().isEmpty()) currentTitle = current == null ? "Local shell" : current.getTitle();
        if (currentTitle == null || currentTitle.trim().isEmpty()) currentTitle = "Local shell";
        terminalTitle.setText(currentTitle);
        int currentIndex = mTermuxService.getIndexOfSession(current);
        terminalSessionButton.setText(String.format(java.util.Locale.US, "%02d / %02d  ˅",
            Math.max(0, currentIndex + 1), Math.max(0, count)));
        LinearLayout recent = mWorkspaceContent == null ? null : (LinearLayout) mWorkspaceContent.findViewWithTag("recent_sessions");
        if (recent == null) return;
        recent.removeAllViews();
        for (TermuxSession termuxSession : mTermuxService.getTermuxSessions()) {
            TerminalSession session = termuxSession.getTerminalSession();
            if (session == null) continue;
            String name = session.mSessionName;
            if (name == null || name.trim().isEmpty()) name = session.getTitle();
            if (name == null || name.trim().isEmpty()) name = "Terminal session";
            String sessionName = name;
            LinearLayout row = premiumCard(false);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView mark = premiumText("›_", 18, true, R.color.neon_violet_soft);
            mark.setGravity(Gravity.CENTER);
            row.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout detail = new LinearLayout(this);
            detail.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            detailParams.leftMargin = dp(10);
            row.addView(detail, detailParams);
            addText(detail, sessionName, 14, true, R.color.glass_text_primary, 0, 3);
            addText(detail, session.isRunning() ? "RUNNING  ·  tap to resume" : "STOPPED  ·  tap to inspect",
                10, false, R.color.glass_text_secondary, 0, 0);
            row.setOnClickListener(v -> {
                mTermuxTerminalSessionActivityClient.setCurrentSession(session);
                selectAppScreen(SCREEN_TERMINAL);
            });
            recent.addView(row, pageLayout());
        }
        if (count == 0) addEmptyState(recent, "No sessions yet", "Create a shell and it will appear here.");
    }

    private void buildFilesPage() {
        mFilesContent.removeAllViews();
        addPageHeader(mFilesContent, "FILE SYSTEM", "Everything\nin its place.",
            "Browse your Termux home and shared storage.");
        LinearLayout locations = new LinearLayout(this);
        locations.setOrientation(LinearLayout.HORIZONTAL);
        addPillButton(locations, "Home", () -> {
            mCurrentFileDirectory = new File(TermuxConstants.TERMUX_HOME_DIR_PATH);
            refreshFileList();
        });
        addPillButton(locations, "Shared storage", () -> {
            mCurrentFileDirectory = new File(TermuxConstants.TERMUX_STORAGE_HOME_DIR_PATH, "shared");
            refreshFileList();
        });
        mFilesContent.addView(locations, pageLayout());

        LinearLayout pathCard = premiumCard(false);
        addText(pathCard, "CURRENT LOCATION", 10, true, R.color.neon_violet_soft, 0, 5);
        mFilesPathLabel = addText(pathCard, "", 12, false, R.color.glass_text_secondary, 0, 0);
        mFilesPathLabel.setMaxLines(2);
        mFilesPathLabel.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        mFilesContent.addView(pathCard, pageLayout());
        mFileList = new LinearLayout(this);
        mFileList.setOrientation(LinearLayout.VERTICAL);
        mFilesContent.addView(mFileList, pageLayout());
        refreshFileList();
    }

    private void refreshFileList() {
        if (mFileList == null || mCurrentFileDirectory == null) return;
        mFilesPathLabel.setText(mCurrentFileDirectory.getAbsolutePath());
        mFileList.removeAllViews();
        if (!mCurrentFileDirectory.equals(new File(TermuxConstants.TERMUX_HOME_DIR_PATH))) {
            addFileRow("‹  Parent folder", "Go up one level", true, () -> {
                File parent = mCurrentFileDirectory.getParentFile();
                if (parent != null) mCurrentFileDirectory = parent;
                refreshFileList();
            });
        }
        File[] entries = mCurrentFileDirectory.listFiles();
        if (entries == null) {
            addEmptyState(mFileList, "Folder unavailable", "This location is not mounted or cannot be read.");
            return;
        }
        Arrays.sort(entries, (left, right) -> {
            if (left.isDirectory() != right.isDirectory()) return left.isDirectory() ? -1 : 1;
            return left.getName().compareToIgnoreCase(right.getName());
        });
        for (File entry : entries) {
            boolean directory = entry.isDirectory();
            String detail = directory ? "FOLDER" : readableFileSize(entry.length());
            addFileRow((directory ? "▰  " : "▤  ") + entry.getName(), detail, directory, () -> {
                if (directory) {
                    mCurrentFileDirectory = entry;
                    refreshFileList();
                } else {
                    showFileActions(entry);
                }
            });
        }
        if (mFileList.getChildCount() == 0) addEmptyState(mFileList, "Nothing here yet", "Files you create in $HOME will show up here.");
    }

    private void addFileRow(String title, String detail, boolean directory, Runnable action) {
        LinearLayout row = premiumCard(false);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addText(text, title, 13, true, R.color.glass_text_primary, 0, 4);
        addText(text, detail, 10, false, R.color.glass_text_secondary, 0, 0);
        TextView arrow = premiumText(directory ? "›" : "⋯", 21, false, R.color.neon_violet_soft);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(v -> action.run());
        mFileList.addView(row, pageLayout());
    }

    private void showFileActions(File file) {
        new AlertDialog.Builder(this)
            .setTitle(file.getName())
            .setMessage(file.getAbsolutePath() + "\n\n" + readableFileSize(file.length()))
            .setNeutralButton("Copy path", (dialog, which) -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("File path", file.getAbsolutePath()));
                showToast("Path copied", false);
            })
            .setPositiveButton("Open in terminal", (dialog, which) -> runCommand("less " + shellQuote(file.getAbsolutePath())))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void buildToolsPage() {
        mToolsContent.removeAllViews();
        addPageHeader(mToolsContent, "COMMAND DECK", "Useful tools.\nOne tap away.",
            "Choose a command and review it before it runs in your shell.");
        addCommandCard(mToolsContent, "Package manager", "Refresh package lists", "pkg update");
        addCommandCard(mToolsContent, "Storage access", "Connect Android shared storage", "termux-setup-storage");
        addCommandCard(mToolsContent, "Device status", "Read battery details via Termux:API", "termux-battery-status");
        addCommandCard(mToolsContent, "Text editor", "Install the lightweight Micro editor", "pkg install micro");
        addText(mToolsContent, "Commands run in your current terminal session after confirmation.",
            11, false, R.color.glass_text_secondary, dp(2), dp(10));
    }

    private void addCommandCard(LinearLayout parent, String title, String subtitle, String command) {
        LinearLayout card = premiumCard(false);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        card.addView(detail, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addText(detail, title, 14, true, R.color.glass_text_primary, 0, 4);
        addText(detail, subtitle, 11, false, R.color.glass_text_secondary, 0, 6);
        TextView code = addText(detail, command, 11, false, R.color.neon_violet_soft, 0, 0);
        code.setTypeface(Typeface.MONOSPACE);
        TextView run = premiumText("RUN  ›", 10, true, R.color.neon_violet_soft);
        card.addView(run, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Run command?")
            .setMessage(command + "\n\nThis will be entered in your current shell.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Run", (dialog, which) -> runCommand(command))
            .show());
        parent.addView(card, pageLayout());
    }

    private void runCommand(String command) {
        if (mTermuxService == null) {
            showToast("Terminal is still starting", false);
            return;
        }
        if (getCurrentSession() == null) mTermuxTerminalSessionActivityClient.addNewSession(false, null);
        selectAppScreen(SCREEN_TERMINAL);
        if (mTerminalView != null && mTerminalView.mEmulator != null) {
            mTerminalView.mEmulator.paste(command + "\n");
        } else {
            showToast("No active terminal session", false);
        }
    }

    private void buildSettingsPage() {
        mSettingsContent.removeAllViews();
        addPageHeader(mSettingsContent, "PERSONALIZE", "Make it yours.",
            "Tune the terminal around how you work.");
        LinearLayout appearance = premiumCard(true);
        addText(appearance, "TERMINAL EXPERIENCE", 10, true, R.color.neon_violet_soft, 0, 6);
        addText(appearance, "Keyboard, colors, font, and behavior", 16, true, R.color.glass_text_primary, 0, 6);
        addText(appearance, "Your existing Termux configuration stays in control.",
            11, false, R.color.glass_text_secondary, 0, 14);
        addPillButton(appearance, "Open preferences", () -> ActivityUtils.startActivity(this,
            new Intent(this, SettingsActivity.class)));
        mSettingsContent.addView(appearance, pageLayout());

        addSectionTitle(mSettingsContent, "Terminal canvas", "Set the balance between more content and larger text.");
        buildTerminalCanvasControls();

        addSectionTitle(mSettingsContent, "App", "Support and information.");
        addSettingsLink(mSettingsContent, "Help & keyboard shortcuts", "Learn the controls", () ->
            ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class)));
        addSettingsLink(mSettingsContent, "More preferences", "Additional app and integration controls", () ->
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class)));
    }

    private void buildTerminalCanvasControls() {
        LinearLayout card = premiumCard(false);
        addText(card, "VIEWPORT PROFILE", 10, true, R.color.neon_violet_soft, 0, 10);

        mTerminalPresetOptions = new LinearLayout(this);
        mTerminalPresetOptions.setOrientation(LinearLayout.HORIZONTAL);
        addTerminalPresetOption("Compact", 0);
        addTerminalPresetOption("Balanced", 1);
        addTerminalPresetOption("Roomy", 2);
        card.addView(mTerminalPresetOptions, pageLayout());

        LinearLayout fontHeader = new LinearLayout(this);
        fontHeader.setGravity(Gravity.CENTER_VERTICAL);
        fontHeader.setOrientation(LinearLayout.HORIZONTAL);
        TextView fontTitle = premiumText("Font size", 13, true, R.color.glass_text_primary);
        fontHeader.addView(fontTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mTerminalFontSizeLabel = premiumText("", 11, true, R.color.neon_violet_soft);
        fontHeader.addView(mTerminalFontSizeLabel, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(fontHeader);

        mTerminalFontSizeSeekBar = new SeekBar(this);
        mTerminalFontSizeSeekBar.setMax(24);
        int fontDp = Math.round(mPreferences.getFontSize() / getResources().getDisplayMetrics().density);
        mTerminalFontSizeSeekBar.setProgress(Math.max(0, Math.min(24, fontDp - 8)));
        card.addView(mTerminalFontSizeSeekBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        addText(card, "Smaller text fits more lines. The terminal grid resizes automatically.",
            10, false, R.color.glass_text_secondary, 0, 0);
        mSettingsContent.addView(card, pageLayout());

        mTerminalFontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int requestedFontDp = progress + 8;
                int requestedFontPx = Math.round(requestedFontDp * getResources().getDisplayMetrics().density);
                mPreferences.setFontSize(requestedFontPx);
                mTerminalView.setTextSize(requestedFontPx);
                mTerminalFontSizeLabel.setText(requestedFontDp + " dp");
                mTerminalCanvasPreset = -1;
                getSharedPreferences(DEVBOX_UI_PREFERENCES, MODE_PRIVATE).edit()
                    .putInt(KEY_TERMINAL_CANVAS_PRESET, -1).apply();
                updateTerminalPresetSelection(-1);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        mTerminalFontSizeLabel.setText(fontDp + " dp");
        updateTerminalPresetSelection(mTerminalCanvasPreset);
    }

    private void addTerminalPresetOption(String label, int index) {
        TextView option = premiumText(label, 10, true, R.color.glass_text_secondary);
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(3), dp(11), dp(3), dp(11));
        option.setBackgroundResource(R.drawable.bg_premium_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        mTerminalPresetOptions.addView(option, params);
        option.setOnClickListener(v -> applyTerminalCanvasPreset(index));
    }

    private void applyTerminalCanvasPreset(int index) {
        int defaultFontPx = TermuxAppSharedPreferences.getDefaultFontSizes(this)[0];
        float density = getResources().getDisplayMetrics().density;
        int insetDp;
        int fontDp;
        switch (index) {
            case 0:
                insetDp = 0;
                fontDp = Math.max(8, Math.round(defaultFontPx / density) - 2);
                break;
            case 2:
                insetDp = 12;
                fontDp = Math.round(defaultFontPx / density) + 2;
                break;
            default:
                insetDp = 5;
                fontDp = Math.round(defaultFontPx / density);
                break;
        }
        mTerminalCanvasInsetDp = insetDp;
        getSharedPreferences(DEVBOX_UI_PREFERENCES, MODE_PRIVATE).edit()
            .putInt(KEY_TERMINAL_CANVAS_INSET, insetDp)
            .putInt(KEY_TERMINAL_CANVAS_PRESET, index).apply();
        mTerminalCanvasPreset = index;
        int fontPx = Math.round(fontDp * density);
        mPreferences.setFontSize(fontPx);
        mTerminalView.setTextSize(fontPx);
        setMargins();
        mTerminalFontSizeLabel.setText(fontDp + " dp");
        mTerminalFontSizeSeekBar.setProgress(Math.max(0, Math.min(24, fontDp - 8)));
        updateTerminalPresetSelection(index);
    }

    private void updateTerminalPresetSelection(int selectedIndex) {
        if (mTerminalPresetOptions == null) return;
        for (int i = 0; i < mTerminalPresetOptions.getChildCount(); i++) {
            TextView option = (TextView) mTerminalPresetOptions.getChildAt(i);
            boolean selected = i == selectedIndex;
            option.setBackgroundResource(selected ? R.drawable.bg_action_pill : R.drawable.bg_premium_card);
            option.setTextColor(getColor(selected ? R.color.glass_text_primary : R.color.glass_text_secondary));
        }
    }

    private void addSettingsLink(LinearLayout parent, String title, String subtitle, Runnable action) {
        LinearLayout card = premiumCard(false);
        addText(card, title, 14, true, R.color.glass_text_primary, 0, 4);
        addText(card, subtitle, 11, false, R.color.glass_text_secondary, 0, 0);
        card.setOnClickListener(v -> action.run());
        parent.addView(card, pageLayout());
    }

    private void addPageHeader(LinearLayout parent, String kicker, String title, String subtitle) {
        addText(parent, kicker, 10, true, R.color.neon_violet_soft, 0, 10);
        TextView heading = addText(parent, title, 30, true, R.color.glass_text_primary, 0, 8);
        heading.setLineSpacing(dp(1), 1f);
        addText(parent, subtitle, 13, false, R.color.glass_text_secondary, 0, 20);
    }

    private void addSectionTitle(LinearLayout parent, String title, String subtitle) {
        addText(parent, title, 18, true, R.color.glass_text_primary, dp(2), 4);
        addText(parent, subtitle, 11, false, R.color.glass_text_secondary, 0, 12);
    }

    private LinearLayout miniCard(String symbol, String title, String detail, Runnable action) {
        LinearLayout card = premiumCard(false);
        addText(card, symbol, 20, true, R.color.neon_violet_soft, 0, 8);
        addText(card, title, 13, true, R.color.glass_text_primary, 0, 4);
        addText(card, detail, 10, false, R.color.glass_text_secondary, 0, 0);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private LinearLayout premiumCard(boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackgroundResource(active ? R.drawable.bg_premium_card_active : R.drawable.bg_premium_card);
        card.setElevation(dp(2));
        return card;
    }

    private TextView addText(LinearLayout parent, String value, int size, boolean bold, int color, int top, int bottom) {
        TextView text = premiumText(value, size, bold, color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        params.bottomMargin = bottom;
        parent.addView(text, params);
        return text;
    }

    private TextView premiumText(String value, int size, boolean bold, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(getColor(color));
        text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    private void addPillButton(LinearLayout parent, String label, Runnable action) {
        TextView button = premiumText(label + "  →", 12, true, R.color.glass_text_primary);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(15), dp(12), dp(15), dp(12));
        button.setBackgroundResource(R.drawable.bg_action_pill);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        parent.addView(button, params);
    }

    private void addEmptyState(LinearLayout parent, String title, String subtitle) {
        LinearLayout card = premiumCard(false);
        addText(card, title, 14, true, R.color.glass_text_primary, 0, 5);
        addText(card, subtitle, 11, false, R.color.glass_text_secondary, 0, 0);
        parent.addView(card, pageLayout());
    }

    private LinearLayout.LayoutParams pageLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        return params;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String readableFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", size / 1024f);
        return String.format(java.util.Locale.US, "%.1f MB", size / (1024f * 1024f));
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    public void openSessionsPanel() {
        if (mSessionsDialog == null) return;
        updateSessionCount();
        mSessionsDialog.show();
    }

    public void closeSessionsPanel() {
        if (mSessionsDialog != null && mSessionsDialog.isShowing()) mSessionsDialog.dismiss();
    }

    private void updateSessionCount() {
        if (mSessionCountView == null || mTermuxService == null) return;
        int count = mTermuxService.getTermuxSessionsSize();
        mSessionCountView.setText(getResources().getQuantityString(R.plurals.label_active_sessions, count, count));
        refreshPremiumShell();
    }





    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (mSessionsDialog != null && mSessionsDialog.isShowing()) {
            closeSessionsPanel();
        } else if (mSelectedScreen == SCREEN_FILES && mCurrentFileDirectory != null &&
            !mCurrentFileDirectory.equals(new File(TermuxConstants.TERMUX_HOME_DIR_PATH))) {
            File parent = mCurrentFileDirectory.getParentFile();
            if (parent != null) mCurrentFileDirectory = parent;
            refreshFileList();
        } else if (mSelectedScreen != SCREEN_WORKSPACE) {
            selectAppScreen(SCREEN_WORKSPACE);
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController != null) mTermuxSessionListViewController.notifyDataSetChanged();
        updateSessionCount();
    }

    public ListView getTermuxSessionsListView() {
        return mTerminalSessionsListView;
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
