package com.example.mysignupapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;

public class requests_adapter extends FirebaseRecyclerAdapter<requests_model, requests_adapter.myviewholder>
{

    public requests_adapter(@NonNull FirebaseRecyclerOptions<requests_model> options) {
        super(options);
    }

    @Override
    protected void onBindViewHolder(@NonNull myviewholder holder, int position, @NonNull final requests_model models) {
        holder.msgtext.setText(models.getText());
        holder.phonetext.setText(models.getSenderId());
        holder.timestamp.setText(models.getTimestamp());

//        holder.img1.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                AppCompatActivity activity=(AppCompatActivity)view.getContext();
//                activity.getSupportFragmentManager().beginTransaction().replace(R.id.wrapper,new descfragment(model.getName(),model.getPhone(),model.getEmail())).addToBackStack(null).commit();
//            }
//        });
    }

    @NonNull
    @Override
    public myviewholder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view= LayoutInflater.from(parent.getContext()).inflate(R.layout.requests_row,parent,false);
        return new myviewholder(view);
    }

    public class myviewholder extends RecyclerView.ViewHolder
    {
        ImageView img1;
        TextView msgtext,phonetext,timestamp;

        public myviewholder(@NonNull View itemView) {
            super(itemView);

            img1=itemView.findViewById(R.id.img1);
            msgtext=itemView.findViewById(R.id.msgtext);
            phonetext=itemView.findViewById(R.id.phonetext);
            timestamp=itemView.findViewById(R.id.timestamptext);
        }
    }

}