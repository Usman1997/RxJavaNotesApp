package com.example.hp.rxjavanotesapp.view;

import android.content.DialogInterface;
import android.graphics.Color;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.hp.rxjavanotesapp.R;
import com.example.hp.rxjavanotesapp.adapter.NoteAdapter;
import com.example.hp.rxjavanotesapp.model.Note;
import com.example.hp.rxjavanotesapp.model.User;
import com.example.hp.rxjavanotesapp.network.ApiClient;
import com.example.hp.rxjavanotesapp.network.ApiService;
import com.example.hp.rxjavanotesapp.utils.MyDividerItemDecoration;
import com.example.hp.rxjavanotesapp.utils.PrefUtils;
import com.example.hp.rxjavanotesapp.utils.RecyclerTouchListener;
import com.jakewharton.retrofit2.adapter.rxjava2.HttpException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private NoteAdapter mAdapter;
    private List<Note> noteList = new ArrayList<>();
    LinearLayoutManager linearLayoutManager;
    private ApiService apiService;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    @BindView(R.id.txt_empty_notes_view)
    TextView txt_empty_notes_view;

    @BindView(R.id.relativeLayout)
    RelativeLayout relativeLayout;

    @BindView(R.id.fab)
    FloatingActionButton floatingActionButton;

    private CompositeDisposable disposable = new CompositeDisposable();

    Unbinder unbinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        unbinder = ButterKnife.bind(this);

        apiService = ApiClient.getClient(MainActivity.this).create(ApiService.class);

        mAdapter = new NoteAdapter(this,noteList);
        linearLayoutManager = new LinearLayoutManager(MainActivity.this);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new MyDividerItemDecoration(this,LinearLayoutManager.VERTICAL,16));
        recyclerView.setAdapter(mAdapter);

        recyclerView.addOnItemTouchListener(new RecyclerTouchListener(this,
                recyclerView, new RecyclerTouchListener.ClickListener() {
            @Override
            public void onClick(View view, int position) {

            }

            @Override
            public void onLongClick(View view, int position) {
                showActionsDialog(position);

            }
        }));


        if(TextUtils.isEmpty(PrefUtils.getApiKey(this))){
            registerUser();
        }else{
            fetchAllNotes();
        }

    }


    @OnClick(R.id.fab)
    public void onClick(){
        showNoteDialog(false, null, -1);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbinder.unbind();
        disposable.dispose();
    }


    private void registerUser(){
        String uniqueId = UUID.randomUUID().toString();
        disposable.add(
                apiService.register(uniqueId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<User>(){

                    @Override
                    public void onSuccess(User user) {
                       PrefUtils.storeApiKey(MainActivity.this,user.getApiKey());

                        Toast.makeText(MainActivity.this,
                                "Device is registered successfully! ApiKey: " + PrefUtils.getApiKey(MainActivity.this),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.e(TAG, "onError: " + e.getMessage());
                        showError(e);
                    }
                }));

    }

    private void fetchAllNotes(){
         disposable.add(
                 apiService.fetchAllNotes()
                 .subscribeOn(Schedulers.io())
                 .observeOn(AndroidSchedulers.mainThread())
                 .map(new Function<List<Note>, List<Note>>() {
                     @Override
                     public List<Note> apply(List<Note> notes) throws Exception {
                         return notes;
                     }
                 }).subscribeWith(new DisposableSingleObserver<List<Note>>() {
                     @Override
                     public void onSuccess(List<Note> notes) {
                         noteList.clear();
                         noteList.addAll(notes);
                         mAdapter.notifyDataSetChanged();

                         toggleEmptyNotes();
                     }

                     @Override
                     public void onError(Throwable e) {
                         Log.e(TAG, "onError: " + e.getMessage());
                         showError(e);
                     }
                 }));
    }

    private void showError(Throwable e){
        String message = "";
        try {
            if (e instanceof IOException) {
                message = "No internet connection!";
            } else if (e instanceof HttpException) {
                HttpException error = (HttpException) e;
                String errorBody = error.response().errorBody().string();
                JSONObject jObj = new JSONObject(errorBody);

                message = jObj.getString("error");
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (JSONException e1) {
            e1.printStackTrace();
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        if (TextUtils.isEmpty(message)) {
            message = "Unknown error occurred! Check LogCat.";
        }

        Snackbar snackbar = Snackbar
                .make(relativeLayout, message, Snackbar.LENGTH_LONG);

        View sbView = snackbar.getView();
        TextView textView = sbView.findViewById(android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.YELLOW);
        snackbar.show();
    }

    private void createNote(String note){
        disposable.add(
                apiService.createNote(note)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<Note>() {
                    @Override
                    public void onSuccess(Note note) {
                        if (!TextUtils.isEmpty(note.getError())) {
                            Toast.makeText(getApplicationContext(), note.getError(), Toast.LENGTH_LONG).show();
                            return;
                        }


                        noteList.add(0, note);
                        mAdapter.notifyItemInserted(0);

                        toggleEmptyNotes();
                    }

                    @Override
                    public void onError(Throwable e) {
                        showError(e);
                    }
                }));
    }

    private void updateNote(int noteId, final String note, final int position){
        disposable.add(
                apiService.updateNote(noteId,note)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver(){

                    @Override
                    public void onComplete() {
                        Note n = noteList.get(position);
                        n.setNote(note);

                        noteList.set(position,n);
                        mAdapter.notifyItemChanged(position);
                    }

                    @Override
                    public void onError(Throwable e) {
                        showError(e);
                    }
                }));
    }

    private void deleteNote(final int noteId,final int position){
        disposable.add(
                apiService.deleteNote(noteId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver() {
                    @Override
                    public void onComplete() {
                        noteList.remove(position);
                        mAdapter.notifyItemRemoved(position);

                        Toast.makeText(MainActivity.this, "Note deleted!", Toast.LENGTH_SHORT).show();

                        toggleEmptyNotes();
                    }

                    @Override
                    public void onError(Throwable e) {
                        showError(e);
                    }
                }));
    }

    private void toggleEmptyNotes(){
        if (noteList.size() > 0) {
            txt_empty_notes_view.setVisibility(View.GONE);
        } else {
            txt_empty_notes_view.setVisibility(View.VISIBLE);
        }
    }

    private void showNoteDialog(final boolean shouldUpdate,final Note note,final int position){
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(getApplicationContext());
        View view = layoutInflaterAndroid.inflate(R.layout.note_dialog, null);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(MainActivity.this);
        alertDialogBuilderUserInput.setView(view);

        final EditText inputNote = view.findViewById(R.id.note);
        TextView dialogTitle = view.findViewById(R.id.dialog_title);
        dialogTitle.setText(!shouldUpdate ? getString(R.string.lbl_new_note_title) : getString(R.string.lbl_edit_note_title));

        if (shouldUpdate && note != null) {
            inputNote.setText(note.getNote());
        }

        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton(shouldUpdate ? "update" : "save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogBox, int id) {

                    }
                })
                .setNegativeButton("cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogBox, int id) {
                                dialogBox.cancel();
                            }
                        });

        final AlertDialog alertDialog = alertDialogBuilderUserInput.create();
        alertDialog.show();


        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Show toast message when no text is entered
                if (TextUtils.isEmpty(inputNote.getText().toString())) {
                    Toast.makeText(MainActivity.this, "Enter note!", Toast.LENGTH_SHORT).show();
                    return;
                } else {
                    alertDialog.dismiss();
                }

                // check if user updating note
                if (shouldUpdate && note != null) {
                    // update note by it's id
                    updateNote(note.getId(), inputNote.getText().toString(), position);
                } else {
                    // create new note
                    createNote(inputNote.getText().toString());
                }
            }
        });
    }


    private void showActionsDialog(final int position) {
        CharSequence colors[] = new CharSequence[]{"Edit", "Delete"};

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose option");
        builder.setItems(colors, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    showNoteDialog(true, noteList.get(position), position);
                } else {
                    deleteNote(noteList.get(position).getId(), position);
                }
            }
        });
        builder.show();
    }
}
