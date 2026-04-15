using BookManagementService.Services;
using BookManagementService.Data;
using BookManagementService.Repositories;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

// Add services
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

// Database
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection")
    ?? "Host=localhost;Database=bookdb;Username=postgres;Password=postgres";
builder.Services.AddDbContext<BookDbContext>(options =>
    options.UseNpgsql(connectionString));

// Repository
builder.Services.AddScoped<IBookRepository, BookRepository>();

// CLIP Service (ONNX)
builder.Services.AddSingleton<IClipService, ClipService>();

// Book Detection Service (OpenCV)
builder.Services.AddSingleton<IBookDetectionService, BookDetectionService>();

// CORS
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader();
    });
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseCors();
app.MapControllers();

// Health check
app.MapGet("/", () => Results.Ok(new { service = "BookManagementService", version = "1.0.0", status = "running" }));

app.Run();
