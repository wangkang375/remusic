package com.wm.remusic.activity;


import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.common.executors.CallerThreadExecutor;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.wm.remusic.R;
import com.wm.remusic.fragment.PlayQueueFragment;
import com.wm.remusic.fragment.RoundFragment;
import com.wm.remusic.fragment.SimpleMoreFragment;
import com.wm.remusic.handler.HandlerUtil;
import com.wm.remusic.info.MusicInfo;
import com.wm.remusic.provider.PlaylistsManager;
import com.wm.remusic.service.MediaService;
import com.wm.remusic.service.MusicPlayer;
import com.wm.remusic.service.MusicTrack;
import com.wm.remusic.uitl.IConstants;
import com.wm.remusic.uitl.ImageUtils;
import com.wm.remusic.uitl.MusicUtils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static com.wm.remusic.service.MusicPlayer.getAlbumPath;


/**
 * Created by wm on 2016/2/21.
 */
public class PlayingActivity extends BaseActivity implements IConstants {
    private ImageView backAlbum, playingmode, control, next, pre, playlist, cmt, fav, down, more, needle;
    private TextView timePlayed, duration;
    private SeekBar mProgress;

    private ActionBar ab;
    private ObjectAnimator needleAnim, animator;
    private AnimatorSet animatorSet;
    private ViewPager mViewPager;
    private FragmentAdapter fAdapter;
    private BitmapFactory.Options newOpts;
    private View activeView;
    private PlaylistsManager playlistsManager;
    private WeakReference<ObjectAnimator> animatorWeakReference;
    private WeakReference<View> viewWeakReference;
    private boolean isFav = false;
    private boolean isNextOrPreSetPage = false; //判断viewpager由手动滑动 还是setcruuentitem换页
    private boolean duetoplaypause = false; //判读是否是播放暂停的通知，不要切换专辑封面
    Toolbar toolbar;
    private FrameLayout albumLayout;


    @Override
    protected void showQuickControl(boolean show) {
        //super.showOrHideQuickControl(show);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playing);
        playlistsManager = PlaylistsManager.getInstance(this);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            ab = getSupportActionBar();
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setHomeAsUpIndicator(R.drawable.actionbar_back);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
        }
        albumLayout = (FrameLayout) (FrameLayout) findViewById(R.id.headerView);
        backAlbum = (ImageView) findViewById(R.id.albumArt);
        playingmode = (ImageView) findViewById(R.id.playing_mode);
        control = (ImageView) findViewById(R.id.playing_play);
        next = (ImageView) findViewById(R.id.playing_next);
        pre = (ImageView) findViewById(R.id.playing_pre);
        playlist = (ImageView) findViewById(R.id.playing_playlist);
        more = (ImageView) findViewById(R.id.playing_more);
        cmt = (ImageView) findViewById(R.id.playing_cmt);
        fav = (ImageView) findViewById(R.id.playing_fav);
        down = (ImageView) findViewById(R.id.playing_down);
        timePlayed = (TextView) findViewById(R.id.music_duration_played);
        duration = (TextView) findViewById(R.id.music_duration);
        mProgress = (SeekBar) findViewById(R.id.play_seek);
        needle = (ImageView) findViewById(R.id.needle);
        mViewPager = (ViewPager) findViewById(R.id.view_pager);
        mProgress.setIndeterminate(false);
        mProgress.setProgress(1);
        //   mProgress.setSecondaryProgress(70);
        //     HandlerUtil.getInstance(this).postDelayed(runnable,1000);
        loadOther();
        setViewPager();
        // setViewPager();
    }

    int progress;
    Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (progress != 100) {
                mProgress.incrementSecondaryProgressBy(progress);
            } else {
                progress += 10;
                HandlerUtil.getInstance(PlayingActivity.this).postDelayed(runnable, 1000);
            }
        }
    };


    private void loadOther() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                needleAnim = ObjectAnimator.ofFloat(needle, "rotation", -30, 0);
                needleAnim.setDuration(60);
                needleAnim.setRepeatMode(0);
                needleAnim.setInterpolator(new LinearInterpolator());

                setSeekBarListener();
                setTools();
            }
        }).start();

    }

    private void setViewPager() {

        PlaybarPagerTransformer transformer = new PlaybarPagerTransformer();
        fAdapter = new FragmentAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(fAdapter);
        mViewPager.setPageTransformer(true, transformer);

        //改变viewpager动画时间
//        try {
//            Field mField = ViewPager.class.getDeclaredField("mScroller");
//            mField.setAccessible(true);
//            MyScroller mScroller = new MyScroller(mViewPager.getContext().getApplicationContext(), new AccelerateInterpolator());
//            mField.set(mViewPager, mScroller);
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalArgumentException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(final int pPosition) {
                if (pPosition < 1) { //首位之前，跳转到末尾（N）
                    MusicPlayer.setQueuePosition(MusicPlayer.getQueue().length);
                    mViewPager.setCurrentItem(MusicPlayer.getQueue().length, false);
                    isNextOrPreSetPage = false;
                    return;

                } else if (pPosition > MusicPlayer.getQueue().length) { //末位之后，跳转到首位（1）
                    MusicPlayer.setQueuePosition(0);
                    mViewPager.setCurrentItem(1, false); //false:不显示跳转过程的动画
                    isNextOrPreSetPage = false;
                    return;
                } else {

                    if (isNextOrPreSetPage == false) {
                        if (pPosition < MusicPlayer.getQueuePosition() + 1) {
                            HandlerUtil.getInstance(PlayingActivity.this).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    MusicPlayer.previous(PlayingActivity.this, true);
                                }
                            }, 396);


                        } else if (pPosition > MusicPlayer.getQueuePosition() + 1) {
                            HandlerUtil.getInstance(PlayingActivity.this).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    MusicPlayer.next();
                                }
                            }, 396);

                        }
                    }

                }
                //MusicPlayer.setQueuePosition(pPosition - 1);
                isNextOrPreSetPage = false;

            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageScrollStateChanged(int pState) {
            }
        });
    }

    private void setTools() {
        playingmode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayer.cycleRepeat();
                updatePlaymode();
            }
        });

        pre.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicPlayer.previous(PlayingActivity.this.getApplication(), true);
            }
        });

        control.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                duetoplaypause = true;


                if (MusicPlayer.isPlaying()) {
                    control.setImageResource(R.drawable.play_rdi_btn_pause);
                } else {
                    control.setImageResource(R.drawable.play_rdi_btn_play);
                }
                if (MusicPlayer.getQueueSize() != 0) {
                    MusicPlayer.playOrPause();
                }
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (animator != null) {
                    animator.end();
                    animator = null;
                }
                MusicPlayer.next();
            }
        });

        playlist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlayQueueFragment playQueueFragment = new PlayQueueFragment();
                playQueueFragment.show(getSupportFragmentManager(), "playlistframent");
            }
        });

        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                SimpleMoreFragment moreFragment = new SimpleMoreFragment().newInstance(MusicPlayer.getCurrentAudioId());
                moreFragment.show(getSupportFragmentManager(), "music");
            }
        });

        fav.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                if (isFav == true) {
                    playlistsManager.removeItem(PlayingActivity.this, IConstants.FAV_PLAYLIST,
                            MusicPlayer.getCurrentAudioId());
                    fav.setImageResource(R.drawable.play_rdi_icn_love);
                    isFav = false;
                } else {
                    playlistsManager.Insert(PlayingActivity.this, IConstants.FAV_PLAYLIST,
                            MusicPlayer.getCurrentAudioId(), 0);
                    fav.setImageResource(R.drawable.play_icn_loved);
                    isFav = true;
                }

                Intent intent = new Intent(IConstants.PLAYLIST_COUNT_CHANGED);
                sendBroadcast(intent);
            }
        });


    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // handle item selection
        if (item.getItemId() == R.id.menu_share) {
            MusicInfo musicInfo = MusicUtils.getMusicInfo(PlayingActivity.this, MusicPlayer.getCurrentAudioId());
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + musicInfo.data));
            shareIntent.setType("audio/*");
            this.startActivity(Intent.createChooser(shareIntent, getResources().getString(R.string.shared_to)));

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.playing_menu, menu);
        return true;

    }

    private void updatePlaymode() {
        if (MusicPlayer.getShuffleMode() == MediaService.SHUFFLE_NORMAL) {
            playingmode.setImageResource(R.drawable.play_icn_shuffle);
            Toast.makeText(PlayingActivity.this.getApplication(), getResources().getString(R.string.random_play),
                    Toast.LENGTH_SHORT).show();
            return;
        } else {
            switch (MusicPlayer.getRepeatMode()) {
                case MediaService.REPEAT_ALL:
                    playingmode.setImageResource(R.drawable.play_icn_loop);
                    Toast.makeText(PlayingActivity.this.getApplication(), getResources().getString(R.string.loop_play),
                            Toast.LENGTH_SHORT).show();
                    break;
                case MediaService.REPEAT_CURRENT:
                    playingmode.setImageResource(R.drawable.play_icn_one);
                    Toast.makeText(PlayingActivity.this.getApplication(), getResources().getString(R.string.play_one),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
//        IntentFilter f = new IntentFilter();
//        f.addAction(MediaService.PLAYSTATE_CHANGED);
//        f.addAction(MediaService.META_CHANGED);
//        f.addAction(MediaService.QUEUE_CHANGED);
//        f.addAction(IConstants.MUSIC_COUNT_CHANGED);
//        registerReceiver(mStatusListener, new IntentFilter(f));

        //设置ViewPager的默认项
        mViewPager.setCurrentItem(MusicPlayer.getQueuePosition() + 1);
        // new setBlurredAlbumArt().execute();

    }

    @Override
    public void onResume() {
        super.onResume();
    }


    public void updateQueue() {
        if (MusicPlayer.getQueueSize() == 0) {
            MusicPlayer.stop();
            finish();
            return;
        }
        fAdapter.notifyDataSetChanged();
        mViewPager.setCurrentItem(MusicPlayer.getQueuePosition() + 1, false);
    }

    private void updateFav(boolean b) {
        if (b == true) {
            fav.setImageResource(R.drawable.play_icn_loved);
        } else {
            fav.setImageResource(R.drawable.play_rdi_icn_love);
        }
    }

    private long bluredId = -1;

    public void updateTrackInfo() {

        if (MusicPlayer.getQueueSize() == 0) {
            return;
        }
        if (!duetoplaypause) {
            isFav = false;
            ArrayList<MusicTrack> favlists = playlistsManager.getPlaylist(IConstants.FAV_PLAYLIST);
            for (int i = 0; i < favlists.size(); i++) {
                if (MusicPlayer.getCurrentAudioId() == favlists.get(i).mId) {
                    isFav = true;
                    break;
                }
            }
            updateFav(isFav);

            if (MusicPlayer.getCurrentAudioId() != bluredId) {
                new setBlurredAlbumArt().execute();
            }
            bluredId = MusicPlayer.getCurrentAudioId();

        }
        duetoplaypause = false;

        Fragment fragment = (RoundFragment) mViewPager.getAdapter().instantiateItem(mViewPager, mViewPager.getCurrentItem());
        viewWeakReference = new WeakReference<View>(fragment.getView());
        activeView = viewWeakReference.get();
        if (activeView != null) {
//            animatorWeakReference = new WeakReference<>((ObjectAnimator) activeView.getTag(R.id.tag_animator));
//            animator = animatorWeakReference.get();
            animator = (ObjectAnimator) activeView.getTag(R.id.tag_animator);
        }

        ab.setTitle(MusicPlayer.getTrackName());
        ab.setSubtitle(MusicPlayer.getArtistName());


        duration.setText(MusicUtils.makeShortTimeString(PlayingActivity.this.getApplication(), MusicPlayer.duration() / 1000));
        //mProgress.setMax((int) MusicPlayer.duration());

        mProgress.postDelayed(mUpdateProgress, 10);


        if (MusicPlayer.isPlaying()) {
            control.setImageResource(R.drawable.play_rdi_btn_pause);

        } else {
            control.setImageResource(R.drawable.play_rdi_btn_play);
        }


        animatorSet = new AnimatorSet();
        if (MusicPlayer.isPlaying()) {
            if (animator != null && !animator.isRunning()) {
                animatorSet.play(needleAnim).before(animator);
                animatorSet.start();
            }

        } else {
            if (needleAnim != null) {
                needleAnim.reverse();
                needleAnim.end();
            }

            if (animator != null && animator.isRunning()) {
                animator.cancel();
                float valueAvatar = (float) animator.getAnimatedValue();
                animator.setFloatValues(valueAvatar, 360f + valueAvatar);
            }
        }


        isNextOrPreSetPage = false;
        if (MusicPlayer.getQueuePosition() + 1 != mViewPager.getCurrentItem()) {
            mViewPager.setCurrentItem(MusicPlayer.getQueuePosition() + 1);
            isNextOrPreSetPage = true;
        }


    }

    @Override
    public void updateTime() {
        duration.setText(MusicUtils.makeShortTimeString(PlayingActivity.this.getApplication(), MusicPlayer.duration() / 1000));
        //mProgress.setMax((int) MusicPlayer.duration());
    }

    @Override
    public void updateBuffer(int p) {
        super.updateBuffer(p);
        mProgress.setSecondaryProgress(p);

    }

    private Runnable mUpdateProgress = new Runnable() {

        @Override
        public void run() {

            if (mProgress != null) {
                long position = MusicPlayer.position();
                long duration = MusicPlayer.duration();
                if (duration > 0)
                    mProgress.setProgress((int) (mProgress.getMax() * position / duration));
                timePlayed.setText(MusicUtils.makeShortTimeString(PlayingActivity.this.getApplication(), position / 1000));
            }

            if (MusicPlayer.isPlaying()) {
                mProgress.postDelayed(mUpdateProgress, 100);
            }
        }
    };

    private void setSeekBarListener() {

        if (mProgress != null)
            mProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                int progress = 0;

                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    if (b) {
                        i = (int) (i * MusicPlayer.duration() / 100);
                        MusicPlayer.seek((long) i);
                        timePlayed.setText(MusicUtils.makeShortTimeString(PlayingActivity.this.getApplication(), i / 1000));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
    }

    private void stopAnim() {
        activeView = null;

        if (animator != null) {
            animator.end();
            animator = null;
        }
        if (needleAnim != null) {
            needleAnim.end();
            needleAnim = null;
        }
        if (animatorSet != null) {
            animatorSet.end();
            animatorSet = null;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        onBackPressed();
        //super.onSaveInstanceState(outState);

    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mProgress.removeCallbacks(mUpdateProgress);
        stopAnim();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        stopAnim();
        mProgress.removeCallbacks(mUpdateProgress);
    }


    public class PlaybarPagerTransformer implements ViewPager.PageTransformer {


        @Override
        public void transformPage(View view, float position) {

            if (position == 0) {
                if (MusicPlayer.isPlaying()) {
                    animator = (ObjectAnimator) view.getTag(R.id.tag_animator);
                    if (animator != null && !animator.isRunning()) {
                        animatorSet = new AnimatorSet();
                        animatorSet.play(needleAnim).before(animator);
                        animatorSet.start();
                    }
                }

            } else if (position == -1 || position == -2 || position == 1) {

                animator = (ObjectAnimator) view.getTag(R.id.tag_animator);
                if (animator != null) {
                    animator.setFloatValues(0);
                    animator.end();
                    animator = null;
                }
            } else {

                if (needleAnim != null) {
                    needleAnim.reverse();
                    needleAnim.end();
                }

                animator = (ObjectAnimator) view.getTag(R.id.tag_animator);
                if (animator != null) {
                    animator.cancel();
                    float valueAvatar = (float) animator.getAnimatedValue();
                    animator.setFloatValues(valueAvatar, 360f + valueAvatar);

                }
            }
        }

    }

    Bitmap mBitmap;

    private class setBlurredAlbumArt extends AsyncTask<Void, Void, Drawable> {
        long albumid = MusicPlayer.getCurrentAlbumId();

        @Override
        protected Drawable doInBackground(Void... loadedImage) {

            Drawable drawable = null;
            mBitmap = null;
            if (newOpts == null) {
                newOpts = new BitmapFactory.Options();
                newOpts.inSampleSize = 6;
                newOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            }

            if (!MusicPlayer.isTrackLocal()) {
                if (getAlbumPath() == null) {
                    mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_disk_210);
                    drawable = ImageUtils.createBlurredImageFromBitmap(mBitmap, PlayingActivity.this.getApplication(), 3);
                    return drawable;
                }
                ImageRequest imageRequest = ImageRequestBuilder
                        .newBuilderWithSource(Uri.parse(getAlbumPath()))
                        .setProgressiveRenderingEnabled(true)
                        .build();

                ImagePipeline imagePipeline = Fresco.getImagePipeline();
                DataSource<CloseableReference<CloseableImage>>
                        dataSource = imagePipeline.fetchDecodedImage(imageRequest, PlayingActivity.this);

                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                                         @Override
                                         public void onNewResultImpl(@Nullable Bitmap bitmap) {
                                             // You can use the bitmap in only limited ways
                                             // No need to do any cleanup.
                                             if (bitmap != null) {
                                                 mBitmap = bitmap;
                                             }
                                             ;

                                         }

                                         @Override
                                         public void onFailureImpl(DataSource dataSource) {
                                             // No cleanup required here.
                                             mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.placeholder_disk_210);

                                         }
                                     },
                        CallerThreadExecutor.getInstance());
                if (mBitmap != null) {
                    drawable = ImageUtils.createBlurredImageFromBitmap(mBitmap, PlayingActivity.this.getApplication(), 3);
                }

            } else {
                try {
                    mBitmap = null;
                    Bitmap bitmap;
                    Uri art = Uri.parse(getAlbumPath());

                    if (art != null) {
                        ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(art, "r");
                        if (fd == null) {
                            return null;
                        }
                        bitmap = BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, newOpts);
                    } else {


                        bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.login_bg_night, newOpts);
                    }
                    if (bitmap != null) {
                        drawable = ImageUtils.createBlurredImageFromBitmap(bitmap, PlayingActivity.this.getApplication(), 3);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            return drawable;
        }

        @Override
        protected void onPostExecute(Drawable result) {

            if (albumid != MusicPlayer.getCurrentAlbumId()) {
                this.cancel(true);
                return;
            }
            setDrawable(result);

        }

    }

    private void setDrawable(Drawable result) {
        if (result != null) {
            if (backAlbum.getDrawable() != null) {
                final TransitionDrawable td =
                        new TransitionDrawable(new Drawable[]{backAlbum.getDrawable(), result});


                backAlbum.setImageDrawable(td);
                //去除过度绘制
                td.setCrossFadeEnabled(true);
                td.startTransition(370);

            } else {
                backAlbum.setImageDrawable(result);
            }
        }
    }

    class FragmentAdapter extends FragmentStatePagerAdapter {

        private int mChildCount = 0;

        public FragmentAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {

            if (position == MusicPlayer.getQueue().length + 1 || position == 0) {
                return RoundFragment.newInstance("");
            }
            // return RoundFragment.newInstance(MusicPlayer.getQueue()[position - 1]);
            return RoundFragment.newInstance(MusicPlayer.getAlbumPathAll()[position - 1]);
        }

        @Override
        public int getCount() {
            //左右各加一个
            return MusicPlayer.getQueue().length + 2;
        }


        @Override
        public void notifyDataSetChanged() {
            mChildCount = getCount();
            super.notifyDataSetChanged();
        }

        @Override
        public int getItemPosition(Object object) {
            if (mChildCount > 0) {
                mChildCount--;
                return POSITION_NONE;
            }
            return super.getItemPosition(object);
        }

    }

    public class MyScroller extends Scroller {
        private int animTime = 390;

        public MyScroller(Context context) {
            super(context);
        }

        public MyScroller(Context context, Interpolator interpolator) {
            super(context, interpolator);
        }

        @Override
        public void startScroll(int startX, int startY, int dx, int dy, int duration) {
            super.startScroll(startX, startY, dx, dy, animTime);
        }

        @Override
        public void startScroll(int startX, int startY, int dx, int dy) {
            super.startScroll(startX, startY, dx, dy, animTime);
        }

        public void setmDuration(int animTime) {
            this.animTime = animTime;
        }
    }


}
