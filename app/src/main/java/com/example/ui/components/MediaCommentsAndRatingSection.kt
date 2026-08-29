package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MediaComment
import com.example.data.model.MediaRatingSummary
import com.example.data.repository.MediaInteractionRepository
import com.example.ui.theme.*
import com.example.ui.viewmodel.StreamViewModel
import com.example.ui.viewmodel.UserProfile
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatCommentFullDateTime(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(date)
}

private fun formatCommentRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 7 * 86400_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

@Composable
fun MediaCommentsAndRatingSection(
    imdbId: String,
    title: String,
    viewModel: StreamViewModel,
    userProfile: UserProfile?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val interactionRepo = remember {
        MediaInteractionRepository(context, coroutineScope)
    }

    // Attach to real-time firestore listeners for this media ID
    LaunchedEffect(imdbId, userProfile?.email) {
        if (imdbId.isNotBlank()) {
            interactionRepo.attachMedia(imdbId, userProfile?.email)
        }
    }

    DisposableEffect(imdbId) {
        onDispose {
            interactionRepo.detach()
        }
    }

    val comments by interactionRepo.commentsState.collectAsState()
    val ratingSummary by interactionRepo.ratingSummaryState.collectAsState()

    var commentInputText by remember { mutableStateOf("") }
    var replyingToComment by remember { mutableStateOf<MediaComment?>(null) }
    var isPostingComment by remember { mutableStateOf(false) }
    var showSignInRequiredDialog by remember { mutableStateOf(false) }
    var signInReasonText by remember { mutableStateOf("Sign in to comment, rate, like, and reply.") }

    // Sign-In Required Modal Dialog
    if (showSignInRequiredDialog) {
        Dialog(onDismissRequest = { showSignInRequiredDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = DeepSlate),
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta))),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.2f), NeonMagenta.copy(alpha = 0.2f))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Sign in Required",
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Authentication Required",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = signInReasonText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Google Sign In Action Button
                    Button(
                        onClick = {
                            viewModel.signInWithGoogle(
                                context = context,
                                onSuccess = {
                                    showSignInRequiredDialog = false
                                    Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("modal_google_signin_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OfficialGoogleLogo(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google",
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = { showSignInRequiredDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(DeepSlate, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // ----------------- RATING HEADER & STARS -----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Rating & Reviews",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (ratingSummary.totalRatings > 0) {
                        "Community Avg: ${ratingSummary.averageRating} / 5.0 (${ratingSummary.totalRatings} ${if (ratingSummary.totalRatings == 1) "vote" else "votes"})"
                    } else {
                        "No ratings yet • Be the first to rate!"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ratingSummary.totalRatings > 0) NeonCyan else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Interactive Star Rating Bar (1 to 5 Stars)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceBlack, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (userProfile != null && ratingSummary.userRating > 0f) {
                    "Your Rating: ${ratingSummary.userRating.toInt()} ★"
                } else {
                    "Tap to Rate:"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ratingSummary.userRating > 0f) NeonMagenta else TextSecondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (star in 1..5) {
                    val isFilled = star <= (if (ratingSummary.userRating > 0f) ratingSummary.userRating else ratingSummary.averageRating)
                    IconButton(
                        onClick = {
                            if (userProfile == null) {
                                signInReasonText = "Please sign in with Google to rate this title."
                                showSignInRequiredDialog = true
                            } else if (ratingSummary.userRating > 0f) {
                                Toast.makeText(context, "You have already rated this title. Only one rating is allowed.", Toast.LENGTH_SHORT).show()
                            } else {
                                interactionRepo.submitRating(
                                    imdbId = imdbId,
                                    user = userProfile,
                                    rating = star.toFloat(),
                                    onSuccess = {
                                        Toast.makeText(context, "Rated $star ★! Thanks for your feedback.", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Rate $star stars",
                            tint = if (isFilled) Color(0xFFFBBF24) else TextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ----------------- COMMENT BOX -----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            if (comments.size > 5) {
                Text(
                    text = "Scrollable feed",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (userProfile == null) {
            // Locked Comment State - Prompts Sign-in
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SpaceBlack),
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sign in to join the conversation",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Sign in with your Google account to post comments, reply, like, and rate.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.signInWithGoogle(
                                context = context,
                                onSuccess = {
                                    Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OfficialGoogleLogo(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign in with Google",
                                color = Color(0xFF1E293B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Unlocked Comment Input Box for Logged-In User
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SpaceBlack, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                // Active Replying Banner
                if (replyingToComment != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(NeonCyan.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "Replying",
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Replying to @${replyingToComment?.userName}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { replyingToComment = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // User Avatar
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(userProfile.avatarUrl.ifEmpty { "https://api.dicebear.com/7.x/bottts/svg?seed=${userProfile.email}" })
                            .crossfade(true)
                            .build(),
                        contentDescription = "My Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(DeepSlate)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (replyingToComment != null) "Replying as ${userProfile.name}" else "Posting as ${userProfile.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = commentInputText,
                    onValueChange = { if (it.length <= 500) commentInputText = it },
                    placeholder = { 
                        Text(
                            text = if (replyingToComment != null) "Write your reply to @${replyingToComment?.userName}..." else "Write a review or comment about this title...",
                            fontSize = 12.sp,
                            color = TextSecondary
                        ) 
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 65.dp, max = 110.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${commentInputText.length}/500",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            if (commentInputText.isNotBlank()) {
                                isPostingComment = true
                                val parent = replyingToComment
                                interactionRepo.postComment(
                                    imdbId = imdbId,
                                    user = userProfile,
                                    text = commentInputText,
                                    rating = if (parent == null) ratingSummary.userRating else 0f,
                                    parentId = parent?.id ?: "",
                                    replyToUserName = parent?.userName ?: "",
                                    onSuccess = {
                                        commentInputText = ""
                                        replyingToComment = null
                                        isPostingComment = false
                                        Toast.makeText(context, if (parent != null) "Reply posted!" else "Comment posted!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        isPostingComment = false
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        enabled = commentInputText.isNotBlank() && !isPostingComment,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonCyan,
                            disabledContainerColor = NeonCyan.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (isPostingComment) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (replyingToComment != null) Icons.AutoMirrored.Filled.Reply else Icons.Default.Send,
                                contentDescription = "Post",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (replyingToComment != null) "Post Reply" else "Post Comment",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ----------------- COMMENTS FEED LIST (SCROLLABLE & THREADED) -----------------
        val topLevelComments = remember(comments) {
            comments.filter { it.parentId.isBlank() }
        }
        val repliesByParent = remember(comments) {
            comments.filter { it.parentId.isNotBlank() }.groupBy { it.parentId }
        }

        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No comments yet. Sign in & be the first to share your thoughts!",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val commentsScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(commentsScrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                topLevelComments.forEach { comment ->
                    val replies = repliesByParent[comment.id] ?: emptyList()
                    CommentItemCard(
                        comment = comment,
                        replies = replies,
                        currentUserEmail = userProfile?.email,
                        isSuperAdmin = userProfile?.isSuperAdmin == true,
                        onLike = {
                            if (userProfile == null) {
                                signInReasonText = "Please sign in with Google to like comments."
                                showSignInRequiredDialog = true
                            } else {
                                interactionRepo.toggleLikeComment(comment.id, userProfile.email)
                            }
                        },
                        onReply = {
                            if (userProfile == null) {
                                signInReasonText = "Please sign in with Google to reply to comments."
                                showSignInRequiredDialog = true
                            } else {
                                replyingToComment = comment
                            }
                        },
                        onDelete = {
                            interactionRepo.deleteComment(comment.id)
                            Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show()
                        },
                        onLikeReply = { reply ->
                            if (userProfile == null) {
                                signInReasonText = "Please sign in with Google to like replies."
                                showSignInRequiredDialog = true
                            } else {
                                interactionRepo.toggleLikeComment(reply.id, userProfile.email)
                            }
                        },
                        onReplyToReply = { reply ->
                            if (userProfile == null) {
                                signInReasonText = "Please sign in with Google to reply."
                                showSignInRequiredDialog = true
                            } else {
                                replyingToComment = comment.copy(userName = reply.userName)
                            }
                        },
                        onDeleteReply = { reply ->
                            interactionRepo.deleteComment(reply.id)
                            Toast.makeText(context, "Reply deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CommentItemCard(
    comment: MediaComment,
    replies: List<MediaComment>,
    currentUserEmail: String?,
    isSuperAdmin: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onLikeReply: (MediaComment) -> Unit,
    onReplyToReply: (MediaComment) -> Unit,
    onDeleteReply: (MediaComment) -> Unit
) {
    val isMyComment = currentUserEmail != null && currentUserEmail.equals(comment.userEmail, ignoreCase = true)
    val isLikedByMe = remember(comment.likedBy, currentUserEmail) {
        val email = currentUserEmail?.trim()?.lowercase() ?: ""
        email.isNotEmpty() && comment.likedBy.any { it.trim().equals(email, ignoreCase = true) }
    }
    
    val fullDateTime = remember(comment.timestamp) { formatCommentFullDateTime(comment.timestamp) }
    val relativeTime = remember(comment.timestamp) { formatCommentRelativeTime(comment.timestamp) }
    
    var showReplies by remember { mutableStateOf(true) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SpaceBlack),
        border = BorderStroke(0.8.dp, if (isMyComment) NeonCyan.copy(alpha = 0.4f) else BorderColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar
                    AsyncImage(
                        model = comment.userAvatar.ifEmpty { "https://api.dicebear.com/7.x/bottts/svg?seed=${comment.userEmail}" },
                        contentDescription = comment.userName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(DeepSlate)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comment.userName.ifBlank { "User" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMyComment) NeonCyan else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (comment.rating > 0f) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFBBF24).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "★ ${comment.rating.toInt()}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Date and Time
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fullDateTime,
                                fontSize = 9.5.sp,
                                color = TextSecondary.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "• $relativeTime",
                                fontSize = 9.5.sp,
                                color = NeonCyan.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                if (isMyComment || isSuperAdmin) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Comment",
                            tint = TextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Comment text
            Text(
                text = comment.text,
                fontSize = 12.5.sp,
                color = TextPrimary,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Like, Reply, Toggle Replies
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Like Action
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onLike() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = if (isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLikedByMe) Color(0xFFFF2D55) else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (comment.likesCount > 0) "${comment.likesCount}" else "Like",
                            fontSize = 11.sp,
                            fontWeight = if (isLikedByMe) FontWeight.Bold else FontWeight.Medium,
                            color = if (isLikedByMe) Color(0xFFFF2D55) else TextSecondary
                        )
                    }

                    // Reply Action
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onReply() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reply",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }

                // Expand/Collapse replies toggle button
                if (replies.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showReplies = !showReplies }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (showReplies) "Hide ${replies.size} ${if (replies.size == 1) "reply" else "replies"}" else "View ${replies.size} ${if (replies.size == 1) "reply" else "replies"}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Icon(
                            imageVector = if (showReplies) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Threaded Replies List
            if (replies.isNotEmpty() && showReplies) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp)
                        .border(
                            BorderStroke(1.dp, NeonCyan.copy(alpha = 0.25f)),
                            RoundedCornerShape(8.dp)
                        )
                        .background(DeepSlate.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    replies.forEach { reply ->
                        ReplyItemView(
                            reply = reply,
                            currentUserEmail = currentUserEmail,
                            isSuperAdmin = isSuperAdmin,
                            onLike = { onLikeReply(reply) },
                            onReply = { onReplyToReply(reply) },
                            onDelete = { onDeleteReply(reply) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyItemView(
    reply: MediaComment,
    currentUserEmail: String?,
    isSuperAdmin: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    val isMyReply = currentUserEmail != null && currentUserEmail.equals(reply.userEmail, ignoreCase = true)
    val isLikedByMe = remember(reply.likedBy, currentUserEmail) {
        val email = currentUserEmail?.trim()?.lowercase() ?: ""
        email.isNotEmpty() && reply.likedBy.any { it.trim().equals(email, ignoreCase = true) }
    }
    
    val fullDateTime = remember(reply.timestamp) { formatCommentFullDateTime(reply.timestamp) }
    val relativeTime = remember(reply.timestamp) { formatCommentRelativeTime(reply.timestamp) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SpaceBlack.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Reply Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = reply.userAvatar.ifEmpty { "https://api.dicebear.com/7.x/bottts/svg?seed=${reply.userEmail}" },
                    contentDescription = reply.userName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(DeepSlate)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reply.userName.ifBlank { "User" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMyReply) NeonCyan else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (reply.replyToUserName.isNotBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "replied to @${reply.replyToUserName}",
                                fontSize = 10.sp,
                                color = NeonMagenta.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Date and Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fullDateTime,
                            fontSize = 9.sp,
                            color = TextSecondary.copy(alpha = 0.75f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "• $relativeTime",
                            fontSize = 9.sp,
                            color = NeonCyan.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (isMyReply || isSuperAdmin) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Reply",
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Reply text
        Text(
            text = reply.text,
            fontSize = 11.5.sp,
            color = TextPrimary,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Actions: Like and Reply
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onLike() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (isLikedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLikedByMe) Color(0xFFFF2D55) else TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (reply.likesCount > 0) "${reply.likesCount}" else "Like",
                    fontSize = 10.sp,
                    fontWeight = if (isLikedByMe) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLikedByMe) Color(0xFFFF2D55) else TextSecondary
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onReply() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Reply,
                    contentDescription = "Reply",
                    tint = TextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "Reply",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
        }
    }
}
