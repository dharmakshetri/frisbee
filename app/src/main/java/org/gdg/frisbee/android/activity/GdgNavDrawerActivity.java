/*
 * Copyright 2013-2015 The GDG Frisbee Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gdg.frisbee.android.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;

import com.google.android.gms.games.Games;
import com.google.api.client.googleapis.services.json.CommonGoogleJsonClientRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Person;

import org.gdg.frisbee.android.BuildConfig;
import org.gdg.frisbee.android.Const;
import org.gdg.frisbee.android.R;
import org.gdg.frisbee.android.adapter.DrawerAdapter;
import org.gdg.frisbee.android.api.GapiOkTransport;
import org.gdg.frisbee.android.app.App;
import org.gdg.frisbee.android.app.OrganizerChecker;
import org.gdg.frisbee.android.eventseries.TaggedEventSeries;
import org.gdg.frisbee.android.eventseries.TaggedEventSeriesActivity;
import org.gdg.frisbee.android.task.Builder;
import org.gdg.frisbee.android.task.CommonAsyncTask;
import org.gdg.frisbee.android.utils.PrefUtils;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;

import butterknife.InjectView;
import butterknife.OnItemClick;
import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

public abstract class GdgNavDrawerActivity extends GdgActivity {

    private final HttpTransport mTransport = new GapiOkTransport();
    private final JsonFactory mJsonFactory = new GsonFactory();
    private Plus mClient;

    protected DrawerAdapter mDrawerAdapter;
    protected ActionBarDrawerToggle mDrawerToggle;
    @InjectView(R.id.drawer)
    DrawerLayout mDrawerLayout;
    @InjectView(R.id.navdrawer_list)
    ListView mDrawerContent;
    @InjectView(R.id.navdrawer_image)
    ImageView mDrawerImage;


    @Override
    public void setContentView(int layoutResId) {
        super.setContentView(layoutResId);

        initNavigationDrawer();
    }

    private void initNavigationDrawer() {
        mDrawerAdapter = new DrawerAdapter(this);
        mDrawerContent.setAdapter(mDrawerAdapter);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.string.drawer_open,  /* "open drawer" description */
                R.string.drawer_close  /* "close drawer" description */
        ) {

            /**
             * Called when a drawer has settled in a completely closed state.
             */
            public void onDrawerClosed(View view) {
                if (PrefUtils.shouldOpenDrawerOnStart(GdgNavDrawerActivity.this)) {
                    PrefUtils.setShouldNotOpenDrawerOnStart(GdgNavDrawerActivity.this);
                }
            }

            /**
             * Called when a drawer has settled in a completely open state.
             */
            public void onDrawerOpened(View drawerView) {
                //getActionBar().setTitle(mDrawerTitle);
            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        String homeChapterId = getSharedPreferences("gdg", MODE_PRIVATE).getString(Const.SETTINGS_HOME_GDG, null);
        if (homeChapterId != null) {
            new Builder<String, Person>(String.class, Person.class)
                    .addParameter(homeChapterId)
                    .setOnBackgroundExecuteListener(new CommonAsyncTask.OnBackgroundExecuteListener<String, Person>() {
                        @Override
                        public Person doInBackground(String... params) {
                            return getPerson(params);
                        }
                    })
                    .setOnPostExecuteListener(new CommonAsyncTask.OnPostExecuteListener<String, Person>() {
                        @Override
                        public void onPostExecute(String[] params, Person person) {
                            if (person != null) {
                                App.getInstance().getPicasso().load(person.getCover().getCoverPhoto().getUrl())
                                        .into(mDrawerImage);
                            }
                        }
                    })
                    .buildAndExecute();
        }

    }

    private Person getPerson(final String[] params) {
        try {
            Person person = (Person) App.getInstance().getModelCache().get("person_" + params[0]);

            if (person == null) {
                Plus.People.Get request = mClient.people().get(params[0]);
                request.setFields("aboutMe,circledByCount,cover/coverPhoto/url,currentLocation,displayName,plusOneCount,tagline,urls");
                person = request.execute();

                App.getInstance().getModelCache().put("person_" + params[0], person, DateTime.now().plusDays(2));
            }
            return person;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("unused")
    @OnItemClick(R.id.navdrawer_list)
    public void onDrawerItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        DrawerAdapter.DrawerItem item = (DrawerAdapter.DrawerItem) mDrawerAdapter.getItem(i);

        switch (item.getId()) {
            case Const.DRAWER_ACHIEVEMENTS:
                if (PrefUtils.isSignedIn(this) && getGoogleApiClient().isConnected()) {
                    startActivityForResult(Games.Achievements.getAchievementsIntent(getGoogleApiClient()), 0);
                } else {
                    Crouton.makeText(GdgNavDrawerActivity.this, getString(R.string.achievements_need_signin), Style.INFO).show();
                }
                break;
            case Const.DRAWER_HOME:
                navigateTo(MainActivity.class, null);
                break;
            case Const.DRAWER_GDE:
                navigateTo(GdeActivity.class, null);
                break;
            case Const.DRAWER_SPECIAL:
                onDrawerSpecialItemClick(item);
                break;
            case Const.DRAWER_PULSE:
                navigateTo(PulseActivity.class, null);
                break;
            case Const.DRAWER_ARROW:
                if (PrefUtils.isSignedIn(this) && getGoogleApiClient().isConnected()) {
                    navigateTo(ArrowActivity.class, null);
                } else {
                    Crouton.makeText(GdgNavDrawerActivity.this, getString(R.string.arrow_need_games), Style.INFO).show();
                }
            case Const.DRAWER_SETTINGS:
                navigateTo(SettingsActivity.class, null);
                break;
            case Const.DRAWER_HELP:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Const.URL_HELP)));
                break;
            case Const.DRAWER_ABOUT:
                navigateTo(AboutActivity.class, null);
                break;
        }
    }

    public void onDrawerSpecialItemClick(DrawerAdapter.DrawerItem item) {

        final ArrayList<TaggedEventSeries> currentEventSeries =
                App.getInstance().currentTaggedEventSeries();
        for (TaggedEventSeries taggedEventSeries : currentEventSeries) {
            if (taggedEventSeries.getDrawerIconResId() == item.getIcon()) {

                Bundle special = new Bundle();
                special.putString(Const.EXTRA_TAGGED_EVENT_CACHEKEY, taggedEventSeries.getTag());
                special.putParcelable(Const.EXTRA_TAGGED_EVENT, taggedEventSeries);
                navigateTo(TaggedEventSeriesActivity.class, special);

                break;
            }
        }
    }

    private void navigateTo(Class<? extends GdgActivity> activityClass, Bundle additional) {
        Intent i = new Intent(GdgNavDrawerActivity.this, activityClass);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        if (additional != null) {
            i.putExtras(additional);
        }

        startActivity(i);
        mDrawerLayout.closeDrawers();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mClient = new Plus.Builder(mTransport, mJsonFactory, null)
                .setGoogleClientRequestInitializer(
                        new CommonGoogleJsonClientRequestInitializer(BuildConfig.IP_SIMPLE_API_ACCESS_KEY))
                .setApplicationName("GDG Frisbee")
                .build();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (PrefUtils.shouldOpenDrawerOnStart(this)) {
            mDrawerLayout.openDrawer(Gravity.START);
        }
    }

    public boolean isOrganizer() {
        return App.getInstance().isOrganizer();
    }

    protected void checkOrganizer(final OrganizerChecker.OrganizerResponseHandler responseHandler) {
        App.getInstance().checkOrganizer(getGoogleApiClient(), responseHandler);
    }
}
